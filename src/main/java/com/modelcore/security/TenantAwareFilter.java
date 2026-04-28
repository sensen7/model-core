package com.modelcore.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 多租户上下文注入过滤器
 * <p>
 * 在每次请求中，从 SecurityContext 的认证信息中提取 tenantId 和 userId，
 * 注入到 TenantContext（ThreadLocal），供后续 Service/Repository 层使用。
 * 请求结束后自动清理，防止内存泄漏。
 * <p>
 * 多实例兼容：优先读取请求头 X-Tenant-Id（用于微服务间内部调用透传租户身份），
 * 若无该请求头则回退从 SecurityContext 中提取（正常用户登录场景）。
 * </p>
 */
@Component
public class TenantAwareFilter extends OncePerRequestFilter {

    /** 微服务间内部调用透传租户 ID 的请求头名称 */
    private static final String TENANT_ID_HEADER = "X-Tenant-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // 优先从请求头读取租户 ID（多实例/微服务内部调用场景）
            String tenantIdHeader = request.getHeader(TENANT_ID_HEADER);
            if (tenantIdHeader != null && !tenantIdHeader.isBlank()) {
                try {
                    TenantContext.setTenantId(Long.parseLong(tenantIdHeader));
                } catch (NumberFormatException ignored) {
                    // 请求头格式非法，忽略，回退到 SecurityContext
                }
            }

            // 若请求头未携带，则从 SecurityContext 提取（用户 Session 登录场景）
            if (TenantContext.getTenantId() == null) {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.getPrincipal() instanceof CustomUserPrincipal principal) {
                    TenantContext.setTenantId(principal.getTenantId());
                    TenantContext.setUserId(principal.getUserId());
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            // 请求结束后必须清理 ThreadLocal
            TenantContext.clear();
        }
    }
}
