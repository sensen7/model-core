package com.modelcore.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/**
 * 自定义用户主体
 * <p>
 * 扩展 Spring Security 的 User，额外携带 userId、tenantId、role 信息，
 * 方便在 Controller 中直接获取当前登录用户的租户上下文。
 * </p>
 */
@Getter
public class CustomUserPrincipal extends User {

    private final Long userId;
    private final Long tenantId;
    private final String role;

    public CustomUserPrincipal(Long userId, Long tenantId, String username,
                               String password, String role,
                               Collection<? extends GrantedAuthority> authorities) {
        super(username, password, authorities);
        this.userId = userId;
        this.tenantId = tenantId;
        this.role = role;
    }
}
