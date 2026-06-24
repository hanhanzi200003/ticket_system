package com.example.ticket_system.config.filter;

import com.example.ticket_system.config.utils.RedisUtil;
import com.example.ticket_system.config.utils.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 幂等性过滤器
 * <p>
 * 防止重复下单、重复取消、重复支付等写操作重复提交。
 * <p>
 * 机制：
 * 1. 客户端对写操作（POST/PUT/DELETE）须在请求头中携带 {@code X-Idempotency-Key}
 * 2. 服务端用 Redis SETNX 判断该 key 是否已处理过
 * 3. 如果已存在，返回 409 Conflict + 上次的响应结果
 * 4. 如果不存在，放行请求，响应后存储结果
 * <p>
 * Redis Key 格式：idempotent:{key}
 * 过期时间：30 秒（覆盖网络抖动导致的重试窗口）
 */
@Slf4j
@Component
public class IdempotencyFilter implements Filter {

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private ObjectMapper objectMapper;

    /** 幂等 Key 请求头 */
    private static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";

    /** Redis Key 前缀 */
    private static final String IDEMPOTENT_PREFIX = "idempotent:";

    /** 幂等 Key 过期时间（秒） */
    private static final long IDEMPOTENT_EXPIRE_SECONDS = 30;

    /** 不需要幂等校验的路径（GET 请求自动跳过，这里配 POST 白名单） */
    private static final String[] SKIP_PATHS = {
            "/auth/login",
            "/auth/regester",
            "/order/list",
            "/order/prepare-pay"
    };

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String method = request.getMethod();

        // 只拦截写操作
        if (!"POST".equalsIgnoreCase(method) && !"PUT".equalsIgnoreCase(method)
                && !"DELETE".equalsIgnoreCase(method) && !"PATCH".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 白名单路径跳过
        String uri = request.getRequestURI();
        for (String skipPath : SKIP_PATHS) {
            if (uri.equals(skipPath) || uri.startsWith(skipPath + "/")) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        // 获取幂等 Key
        String idempotencyKey = request.getHeader(IDEMPOTENCY_HEADER);

        if (idempotencyKey == null || idempotencyKey.isEmpty()) {
            // 没有幂等 Key，使用 userId + URI 自动生成
            // 同一用户对同一写接口在 30 秒内只能提交一次
            Object userId = request.getAttribute("userId");
            if (userId == null) {
                // 未登录的写操作，要求必须传幂等 Key
                sendError(response, 400, "写操作必须携带 X-Idempotency-Key 请求头");
                return;
            }
            idempotencyKey = userId + ":" + uri;
        }

        // 校验 key 格式：只允许字母数字和短横线
        if (!idempotencyKey.matches("^[a-zA-Z0-9_\\-:]+$")) {
            sendError(response, 400, "X-Idempotency-Key 格式非法，仅允许字母数字和短横线");
            return;
        }

        String redisKey = IDEMPOTENT_PREFIX + idempotencyKey;

        // SETNX 判断是否重复
        if (!redisUtil.setNx(redisKey, "processing", IDEMPOTENT_EXPIRE_SECONDS)) {
            // key 已存在，说明是重复请求
            String existingResult = redisUtil.get(redisKey);
            if (existingResult != null && !"processing".equals(existingResult)) {
                // 之前的请求已完成，返回缓存的结果
                log.info("[幂等] 重复请求，返回缓存结果：key={}", idempotencyKey);
                response.setContentType("application/json;charset=UTF-8");
                response.setStatus(200);
                response.getWriter().write(existingResult);
                return;
            }
            // 之前的请求还在处理中
            log.warn("[幂等] 请求正在处理中，拒绝重复提交：key={}", idempotencyKey);
            sendError(response, 409, "请求正在处理中，请勿重复提交");
            return;
        }

        // 使用包装 Response 来捕获响应结果
        IdempotentResponseWrapper wrappedResponse = new IdempotentResponseWrapper(response);

        try {
            filterChain.doFilter(request, wrappedResponse);
        } catch (Exception e) {
            // 请求处理失败，删除幂等标记，允许重试
            redisUtil.delete(redisKey);
            throw e;
        }

        // 请求处理完成，缓存响应结果
        if (wrappedResponse.getStatus() == 200) {
            String responseBody = new String(wrappedResponse.getContent(), StandardCharsets.UTF_8);
            redisUtil.set(redisKey, responseBody, IDEMPOTENT_EXPIRE_SECONDS);
        } else {
            // 非 200 响应，删除幂等标记，允许重试
            redisUtil.delete(redisKey);
        }
    }

    /**
     * 可重复读取的 Response 包装器
     */
    private static class IdempotentResponseWrapper extends HttpServletResponseWrapper {
        private final java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        private ServletOutputStream outputStream;

        public IdempotentResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public ServletOutputStream getOutputStream() {
            if (outputStream == null) {
                outputStream = new ServletOutputStream() {
                    @Override
                    public void write(int b) {
                        buffer.write(b);
                    }

                    @Override
                    public void write(byte[] b) {
                        buffer.write(b, 0, b.length);
                    }

                    @Override
                    public void write(byte[] b, int off, int len) {
                        buffer.write(b, off, len);
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setWriteListener(WriteListener writeListener) {
                        // no-op
                    }
                };
            }
            return outputStream;
        }

        @Override
        public java.io.PrintWriter getWriter() {
            return new java.io.PrintWriter(new java.io.OutputStreamWriter(buffer, StandardCharsets.UTF_8), true);
        }

        public byte[] getContent() {
            return buffer.toByteArray();
        }
    }

    private void sendError(HttpServletResponse response, int code, String message) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(code);
        response.getWriter().write(objectMapper.writeValueAsString(
                new Result<>(code, message, null)
        ));
    }
}
