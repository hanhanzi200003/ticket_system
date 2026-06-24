package com.example.ticket_system.config;

import com.example.ticket_system.config.filter.IdempotencyFilter;
import com.example.ticket_system.config.filter.RateLimitFilter;
import com.example.ticket_system.config.filter.RequestValidationFilter;
import com.example.ticket_system.config.filter.TokenFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 安全配置
 * <p>
 * 过滤器执行顺序（数字越小越先执行）：
 * 1. RequestValidationFilter  — 非法请求拦截（最先执行，挡住恶意请求）
 * 2. RateLimitFilter          — Redis 限流（防止暴力请求）
 * 3. TokenFilter              — 登录认证（验证 token + 角色权限）
 * 4. IdempotencyFilter        — 幂等性校验（防止重复提交写操作）
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private TokenFilter tokenFilter;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @Autowired
    private IdempotencyFilter idempotencyFilter;

    @Autowired
    private RequestValidationFilter requestValidationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                );
        // TokenFilter 通过 FilterRegistrationBean 注册，不再通过 Spring Security 注册
        return http.build();
    }

    /**
     * 注册请求安全过滤器（Order=1，最先执行）
     */
    @Bean
    public FilterRegistrationBean<RequestValidationFilter> requestValidationFilterRegistration() {
        FilterRegistrationBean<RequestValidationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(requestValidationFilter);
        registration.addUrlPatterns("/*");
        registration.setOrder(1);
        registration.setName("requestValidationFilter");
        return registration;
    }

    /**
     * 注册限流过滤器（Order=2）
     */
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration() {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(rateLimitFilter);
        registration.addUrlPatterns("/*");
        registration.setOrder(2);
        registration.setName("rateLimitFilter");
        return registration;
    }

    /**
     * 注册 Token 过滤器（Order=3，认证 + 角色权限）
     */
    @Bean
    public FilterRegistrationBean<TokenFilter> tokenFilterRegistration() {
        FilterRegistrationBean<TokenFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(tokenFilter);
        registration.addUrlPatterns("/*");
        registration.setOrder(3);
        registration.setName("tokenFilter");
        return registration;
    }

    /**
     * 注册幂等性过滤器（Order=4，在 TokenFilter 之后）
     */
    @Bean
    public FilterRegistrationBean<IdempotencyFilter> idempotencyFilterRegistration() {
        FilterRegistrationBean<IdempotencyFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(idempotencyFilter);
        registration.addUrlPatterns("/*");
        registration.setOrder(4);
        registration.setName("idempotencyFilter");
        return registration;
    }
}
