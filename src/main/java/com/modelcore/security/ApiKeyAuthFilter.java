package com.modelcore.security;

import com.modelcore.entity.ApiKey;
import com.modelcore.repository.ApiKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * API Key 认证过滤器
 * <p>
 * 拦截 /v1/** 路径的请求，从 Authorization: Bearer sk-xxx 头中提取 API Key，
 * 验证 Key 的有效性（存在、状态、过期时间），通过后将租户信息注入 SecurityContext。
 * 非 /v1/** 路径的请求直接放行，交给后续的表单登录认证处理。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final ApiKeyRepository apiKeyRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // 仅拦截 API 代理路径
        if (!path.startsWith("/v1/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer sk-")) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "缺少有效的 API Key");
            return;
        }

        String keyValue = authHeader.substring(7); // 去掉 "Bearer " 前缀
        Optional<ApiKey> optKey = apiKeyRepository.findByKeyValue(keyValue);

        if (optKey.isEmpty()) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "API Key 不存在");
            return;
        }

        ApiKey apiKey = optKey.get();

        // 检查 Key 状态
        if (!"ACTIVE".equals(apiKey.getStatus())) {
            sendError(response, HttpServletResponse.SC_FORBIDDEN, "API Key 已被禁用");
            return;
        }

        // 检查过期时间
        if (apiKey.getExpiresAt() != null && apiKey.getExpiresAt().isBefore(LocalDateTime.now())) {
            sendError(response, HttpServletResponse.SC_FORBIDDEN, "API Key 已过期");
            return;
        }

        // 认证通过，构建 Authentication 并注入 SecurityContext
        ApiKeyPrincipal principal = new ApiKeyPrincipal(
                apiKey.getId(), apiKey.getTenantId(),
                apiKey.getKeyValue(), apiKey.getRateLimitPerMinute(),
                apiKey.getMonthlyLimit());
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_API_USER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 同时注入 TenantContext
        TenantContext.setTenantId(apiKey.getTenantId());

        try {
            filterChain.doFilter(request, response);
        } finally {
            // 请求结束后必须清理 ThreadLocal，防止内存泄漏
            TenantContext.clear();
        }
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":{\"message\":\"" + message + "\",\"type\":\"authentication_error\"}}");
    }
}
