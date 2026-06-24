package com.example.ticket_system.config.filter;

import com.example.ticket_system.config.utils.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 请求安全过滤器
 * <p>
 * 统一拦截所有请求，防止非法请求：
 * 1. URI 路径合法性校验（防止路径穿越、SQL注入等）
 * 2. HTTP 方法白名单
 * 3. Content-Type 校验
 * 4. 请求体大小限制
 * 5. 非法字符过滤
 */
@Slf4j
@Component
public class RequestValidationFilter implements Filter {

    @Autowired
    private ObjectMapper objectMapper;

    /** 允许的 HTTP 方法 */
    private static final Set<String> ALLOWED_METHODS = Set.of(
            "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"
    );

    /** 请求体最大大小（1MB） */
    private static final int MAX_CONTENT_LENGTH = 1024 * 1024;

    /** 非法 URI 字符模式：路径穿越、特殊字符 */
    private static final Pattern ILLEGAL_URI_PATTERN = Pattern.compile(
            "(\\.\\.)|" +                          // 路径穿越 ../
            "(<|>)|" +                             // XSS 尖括号
            "(\\|)|" +                             // 管道符
            "(`)|" +                               // 反引号
            "(\\$\\{)|" +                          // EL 表达式注入
            "(%00)|" +                             // 空字节注入
            "(%0[adAD])|" +                        // CRLF 注入
            "(\\\\x[0-9a-fA-F]{2})"                // 十六进制编码注入
    );

    /** SQL 注入关键词模式（URI 中不应出现） */
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
            "(?i)(union\\s+select)|" +             // UNION SELECT
            "(?i)(insert\\s+into)|" +              // INSERT INTO
            "(?i)(delete\\s+from)|" +              // DELETE FROM
            "(?i)(drop\\s+table)|" +               // DROP TABLE
            "(?i)(alter\\s+table)|" +              // ALTER TABLE
            "(?i)(exec\\s*\\()|" +                 // EXEC(
            "(?i)(execute\\s*\\()|" +              // EXECUTE(
            "(?i)(script\\s*:)|" +                 // script:
            "(?i)(javascript:)",                    // javascript:
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // 1. HTTP 方法白名单
        String method = request.getMethod();
        if (!ALLOWED_METHODS.contains(method.toUpperCase())) {
            log.warn("[安全] 非法 HTTP 方法：method={}, uri={}, ip={}",
                    method, request.getRequestURI(), request.getRemoteAddr());
            sendError(response, 405, "不支持的请求方法");
            return;
        }

        // 2. URI 合法性校验
        String uri = request.getRequestURI();
        if (ILLEGAL_URI_PATTERN.matcher(uri).find()) {
            log.warn("[安全] 非法 URI 字符：uri={}, ip={}", uri, request.getRemoteAddr());
            sendError(response, 400, "请求路径包含非法字符");
            return;
        }

        if (SQL_INJECTION_PATTERN.matcher(uri).find()) {
            log.warn("[安全] 疑似 SQL 注入：uri={}, ip={}", uri, request.getRemoteAddr());
            sendError(response, 400, "请求路径包含非法字符");
            return;
        }

        // 3. URI 长度限制
        if (uri.length() > 500) {
            log.warn("[安全] URI 过长：length={}, ip={}", uri.length(), request.getRemoteAddr());
            sendError(response, 414, "请求路径过长");
            return;
        }

        // 4. 请求体大小限制
        int contentLength = request.getContentLength();
        if (contentLength > MAX_CONTENT_LENGTH) {
            log.warn("[安全] 请求体过大：size={}, uri={}, ip={}",
                    contentLength, uri, request.getRemoteAddr());
            sendError(response, 413, "请求体过大，最大允许1MB");
            return;
        }

        // 5. Content-Type 校验（POST/PUT/PATCH 必须是 JSON）
        if (("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method)) && request.getContentLength() > 0) {
            String contentType = request.getContentType();
            if (contentType != null && !contentType.contains("application/json")
                    && !contentType.contains("multipart/form-data")) {
                log.warn("[安全] 非法 Content-Type：contentType={}, uri={}, ip={}",
                        contentType, uri, request.getRemoteAddr());
                sendError(response, 415, "仅支持 application/json 格式");
                return;
            }
        }

        // 6. 查询参数校验
        String queryString = request.getQueryString();
        if (queryString != null) {
            if (ILLEGAL_URI_PATTERN.matcher(queryString).find()
                    || SQL_INJECTION_PATTERN.matcher(queryString).find()) {
                log.warn("[安全] 非法查询参数：query={}, uri={}, ip={}",
                        queryString, uri, request.getRemoteAddr());
                sendError(response, 400, "查询参数包含非法字符");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void sendError(HttpServletResponse response, int code, String message) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(code);
        response.getWriter().write(objectMapper.writeValueAsString(
                new Result<>(code, message, null)
        ));
    }
}
