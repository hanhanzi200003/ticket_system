package com.example.ticket_system.config.filter;

import com.example.ticket_system.config.utils.TokenUtil;
import com.example.ticket_system.config.utils.UserInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
public class TokenFilter implements Filter {

    @Autowired
    private TokenUtil tokenUtil;

    @Autowired
    private ObjectMapper objectMapper;

    // 不需要token验证的路径
    private static final String[] WHITE_LIST = {
            "/auth/login",
            "/auth/regester",
            "/error",
            "/css/",
            "/js/",
            "/login.html",
            "/index.html",
            "/orders.html",
            "/detail.html",
            "/pay.html",
            "/merchant.html",
            "/admin.html"
    };

    // 仅管理员可访问的路径前缀
    private static final List<String> ADMIN_PATHS = Arrays.asList(
            "/admin/"
    );

    // 商家和管理员可访问的路径前缀（普通用户不可访问）
    private static final List<String> MERCHANT_PATHS = Arrays.asList(
            "/merchant/"
    );

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String uri = request.getRequestURI();

        // 根路径直接放行（由 WebConfig 重定向到 /index.html）
        if ("/".equals(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 白名单路径直接放行
        for(String whiteUri : WHITE_LIST){
            if(uri.equals(whiteUri) || uri.startsWith(whiteUri) || uri.startsWith("/swagger") || uri.startsWith("/v3/api-docs")){
                filterChain.doFilter(request, response);
                return;
            }
        }

        // 从请求头获取token
        String token = request.getHeader("Authorization");
        if(token == null || token.isEmpty()){
            token = request.getHeader("token");
        }
        // 处理Bearer token格式
        if(token != null && token.startsWith("Bearer ")){
            token = token.substring(7);
        }

        if(token == null || token.isEmpty()){
            sendError(response, 401, "未登录，请先登录！");
            return;
        }

        // 验证token
        UserInfo userInfo = tokenUtil.verifyToken(token);
        if(userInfo == null){
            sendError(response, 401, "登录已过期，请重新登录！");
            return;
        }

        // 检查用户状态（status=0表示禁用）
        if(userInfo.getStatus() != null && userInfo.getStatus() == 0){
            sendError(response, 403, "账号已被禁用，请联系管理员！");
            return;
        }

        // 角色权限校验
        String userRole = userInfo.getRole();
        if(userRole == null || userRole.isEmpty()){
            sendError(response, 403, "用户角色异常！");
            return;
        }

        // 管理员路径校验
        for(String adminPath : ADMIN_PATHS){
            if(uri.startsWith(adminPath)){
                if(!"admin".equals(userRole)){
                    sendError(response, 403, "权限不足，仅管理员可访问！");
                    return;
                }
                break;
            }
        }

        // 商家路径校验（仅商家和管理员可访问）
        for(String merchantPath : MERCHANT_PATHS){
            if(uri.startsWith(merchantPath)){
                if(!"merchant".equals(userRole) && !"admin".equals(userRole)){
                    sendError(response, 403, "权限不足，仅商家可访问！");
                    return;
                }
                break;
            }
        }

        // 将用户信息存入请求属性，供后续Controller使用
        request.setAttribute("userInfo", userInfo);
        request.setAttribute("userId", userInfo.getUserId());
        request.setAttribute("userRole", userInfo.getRole());

        // JWT 无状态，无需刷新有效期（黑名单由 TokenUtil 内部处理）

        // 放行
        filterChain.doFilter(request, response);
    }

    /**
     * 返回错误信息
     */
    private void sendError(HttpServletResponse response, int code, String message) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(code);
        response.getWriter().write(objectMapper.writeValueAsString(
                new com.example.ticket_system.config.utils.Result<>(code, message, null)
        ));
    }
}
