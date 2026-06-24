package com.example.ticket_system.config.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT Token 工具类（无状态 + Redis 黑名单）
 *
 * 核心设计：
 *   - JWT 本身携带用户信息（userId, role, status），无需 Redis 存储会话
 *   - Redis 仅用于黑名单（登出/踢人/封禁时将 token 的 jti 加入黑名单）
 *   - 黑名单过期时间 = JWT 剩余有效期（过期后自动清理）
 *   - Redis 不可用时：JWT 仍可验证，黑名单失效（降级策略：宁可放行不可拒绝）
 */
@Slf4j
@Component
public class TokenUtil {

    @Autowired
    private RedisUtil redisUtil;

    /** JWT 签名密钥（至少 256 位，建议 32+ 字符） */
    private static final String JWT_SECRET = "ticket-system-jwt-secret-key-must-be-at-least-256-bits-long";

    /** JWT 签名密钥对象 */
    private final SecretKey signingKey = Keys.hmacShaKeyFor(
            JWT_SECRET.getBytes(StandardCharsets.UTF_8));

    /** Token 过期时间：24 小时（毫秒） */
    private static final long TOKEN_EXPIRE_MS = 24 * 60 * 60 * 1000L;

    /** Redis 黑名单 Key 前缀 */
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    /** Redis 用户黑名单 Key 前缀（踢人/封禁时，将该用户所有 token 失效） */
    private static final String USER_BLACKLIST_PREFIX = "jwt:user:blacklist:";

    // ==================== 创建 Token ====================

    /**
     * 创建 JWT Token
     *
     * @param userInfo 用户信息
     * @return JWT 字符串
     */
    public String createToken(UserInfo userInfo) {
        long now = System.currentTimeMillis();
        Date issuedAt = new Date(now);
        Date expiration = new Date(now + TOKEN_EXPIRE_MS);

        return Jwts.builder()
                .subject(String.valueOf(userInfo.getUserId()))
                .claim("role", userInfo.getRole())
                .claim("status", userInfo.getStatus() != null ? userInfo.getStatus() : 1)
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(signingKey)
                .compact();
    }

    // ==================== 验证 Token ====================

    /**
     * 验证 JWT Token 并返回用户信息
     *
     * @param token JWT 字符串
     * @return 用户信息，验证失败返回 null
     */
    public UserInfo verifyToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // 检查是否在黑名单中
            if (isBlacklisted(token, claims)) {
                log.debug("Token 已被加入黑名单");
                return null;
            }

            // 检查用户是否被全局拉黑（踢人/封禁）
            String userId = claims.getSubject();
            if (isUserBlacklisted(userId, claims)) {
                log.debug("用户已被全局拉黑：userId={}", userId);
                return null;
            }

            // 构建 UserInfo
            UserInfo userInfo = new UserInfo();
            userInfo.setUserId(Long.parseLong(userId));
            userInfo.setRole(claims.get("role", String.class));
            userInfo.setStatus(claims.get("status", Integer.class));

            return userInfo;

        } catch (ExpiredJwtException e) {
            log.debug("Token 已过期");
            return null;
        } catch (Exception e) {
            log.debug("Token 验证失败：{}", e.getMessage());
            return null;
        }
    }

    // ==================== 黑名单管理 ====================

    /**
     * 将 Token 加入黑名单（登出时使用）
     *
     * @param token JWT 字符串
     */
    public void blacklistToken(String token) {
        try {
            Claims claims = parseClaimsUnverified(token);
            if (claims == null) {
                return;
            }

            // 黑名单过期时间 = JWT 剩余有效期（JWT 过期后黑名单自动失效，节省 Redis 内存）
            long remainingMs = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (remainingMs <= 0) {
                return; // Token 已过期，无需加入黑名单
            }

            String blacklistKey = BLACKLIST_PREFIX + claims.getId();
            redisUtil.set(blacklistKey, "1", remainingMs / 1000 + 1);
            log.info("Token 已加入黑名单：userId={}, 剩余有效期={}秒", claims.getSubject(), remainingMs / 1000);
        } catch (Exception e) {
            log.warn("加入黑名单失败（Redis 不可用）：{}", e.getMessage());
        }
    }

    /**
     * 将用户的所有 Token 加入黑名单（踢人/封禁时使用）
     * <p>
     * 记录一个"截止时间"，所有签发时间早于此截止时间的 Token 都视为无效。
     *
     * @param userId 用户 ID
     */
    public void blacklistUser(Long userId) {
        try {
            // 记录当前时间戳，所有签发时间早于此的 Token 都失效
            String userBlacklistKey = USER_BLACKLIST_PREFIX + userId;
            long now = System.currentTimeMillis();
            // 过期时间 = 24 小时（最大 Token 有效期，过期后自动清理）
            redisUtil.set(userBlacklistKey, String.valueOf(now), TOKEN_EXPIRE_MS / 1000);
            log.info("用户已加入全局黑名单：userId={}", userId);
        } catch (Exception e) {
            log.warn("加入用户黑名单失败（Redis 不可用）：{}", e.getMessage());
        }
    }

    /**
     * 检查 Token 是否在黑名单中
     */
    private boolean isBlacklisted(String token, Claims claims) {
        try {
            String blacklistKey = BLACKLIST_PREFIX + claims.getId();
            return redisUtil.hasKey(blacklistKey);
        } catch (Exception e) {
            // Redis 不可用时，黑名单失效，放行（降级策略）
            log.warn("检查黑名单失败（Redis 不可用，放行）：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 检查用户是否被全局拉黑
     * <p>
     * 如果用户被拉黑，记录了一个截止时间戳，
     * 所有签发时间(issuedAt)早于此截止时间的 Token 都无效。
     */
    private boolean isUserBlacklisted(String userId, Claims claims) {
        try {
            String userBlacklistKey = USER_BLACKLIST_PREFIX + userId;
            String cutoffTimeStr = redisUtil.get(userBlacklistKey);
            if (cutoffTimeStr == null) {
                return false; // 不在黑名单中
            }

            long cutoffTime = Long.parseLong(cutoffTimeStr);
            // Token 的签发时间早于截止时间 → 被拉黑
            return claims.getIssuedAt().getTime() < cutoffTime;
        } catch (Exception e) {
            // Redis 不可用时，黑名单失效，放行
            log.warn("检查用户黑名单失败（Redis 不可用，放行）：{}", e.getMessage());
            return false;
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 解析 Token 的 Claims（不验证签名和过期时间，用于已过期 Token 的黑名单操作）
     */
    private Claims parseClaimsUnverified(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            // Token 过期但仍能获取 Claims
            return e.getClaims();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 删除 Token（登出 — 加入黑名单）
     *
     * @param token JWT 字符串
     */
    public void deleteToken(String token) {
        blacklistToken(token);
    }

    /**
     * 根据用户 ID 将所有 Token 失效（踢人下线/封禁）
     *
     * @param userId 用户 ID
     */
    public void deleteTokenByUserId(Long userId) {
        blacklistUser(userId);
    }

    /**
     * 刷新 Token 有效期（JWT 无状态，不支持续期，需要客户端重新登录）
     * <p>
     * 保留此方法以兼容 TokenFilter 的调用，实际为空操作。
     * 如需实现"无感刷新"，可在 Token 即将过期时返回新 Token 给客户端。
     */
    public boolean refreshToken(String token) {
        // JWT 无状态，无需刷新。返回 true 表示 Token 仍有效。
        return verifyToken(token) != null;
    }
}
