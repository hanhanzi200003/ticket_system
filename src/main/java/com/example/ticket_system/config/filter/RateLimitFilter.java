package com.example.ticket_system.config.filter;

import com.example.ticket_system.config.utils.RedisUtil;
import com.example.ticket_system.config.utils.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Redis 限流过滤器
 * <p>
 * 使用 Redis 计数器实现滑动窗口限流：
 * - 每个用户 ID 每秒最多 5 个请求
 * - 未登录用户使用 IP 地址限流
 * <p>
 * Redis Key 格式：rate_limit:{userIdOrIp}:{second}
 * 过期时间：2 秒（保证计数器在当前秒和下一秒后自动清理）
 */
@Slf4j
@Component
public class RateLimitFilter implements Filter {

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private ObjectMapper objectMapper;

    /** 每秒最大请求数 */
    private static final int MAX_REQUESTS_PER_SECOND = 5;

    /** 限流 Key 前缀 */
    private static final String RATE_LIMIT_PREFIX = "rate_limit:";

    /** 计数器过期时间（秒），略大于1秒确保当前秒的计数器不会提前消失 */
    private static final long COUNTER_EXPIRE_SECONDS = 2;

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // 获取用户标识：优先用 userId，未登录用 IP
        String identity = resolveIdentity(request);

        // 按秒粒度计数
        long currentSecond = System.currentTimeMillis() / 1000;
        String counterKey = RATE_LIMIT_PREFIX + identity + ":" + currentSecond;

        // Redis INCR + EXPIRE（原子操作用 Lua 脚本保证）
        long count = incrementWithExpire(counterKey, COUNTER_EXPIRE_SECONDS);

        if (count > MAX_REQUESTS_PER_SECOND) {
            log.warn("[限流] 请求过于频繁：identity={}, uri={}, count={}", identity, request.getRequestURI(), count);
            sendError(response, 429, "请求过于频繁，请稍后再试");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 原子递增 + 设置过期（Lua 脚本保证原子性）
     */
    private long incrementWithExpire(String key, long expireSeconds) {
        if (redisUtil.shouldDegraded()) {
            // Redis 不可用时放行，避免影响正常业务
            return 0;
        }
        try {
            String luaScript =
                "local count = redis.call('incr', KEYS[1]) " +
                "if count == 1 then " +
                "    redis.call('expire', KEYS[1], ARGV[1]) " +
                "end " +
                "return count";
            Long result = redisUtil.getRedisTemplate().execute(
                    new org.springframework.data.redis.core.script.DefaultRedisScript<>(luaScript, Long.class),
                    java.util.Collections.singletonList(key),
                    String.valueOf(expireSeconds));
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("[限流] Redis 递增失败，放行请求", e);
            return 0;
        }
    }

    /**
     * 解析用户标识
     */
    private String resolveIdentity(HttpServletRequest request) {
        // 优先从请求属性获取 userId（TokenFilter 已设置）
        Object userIdAttr = request.getAttribute("userId");
        if (userIdAttr != null) {
            return "u:" + userIdAttr;
        }
        // 未登录用户使用 IP
        String ip = getClientIp(request);
        return "ip:" + ip;
    }

    /**
     * 获取客户端真实 IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private void sendError(HttpServletResponse response, int code, String message) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(code);
        response.getWriter().write(objectMapper.writeValueAsString(
                new Result<>(code, message, null)
        ));
    }
}
