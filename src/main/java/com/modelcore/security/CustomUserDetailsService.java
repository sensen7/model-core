package com.modelcore.security;

import com.modelcore.entity.User;
import com.modelcore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 自定义用户认证加载服务
 * <p>
 * 供 Spring Security 表单登录使用，从数据库加载用户信息。
 * 返回的 UserDetails 中包含 ROLE_ 前缀的角色权限。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new UsernameNotFoundException("用户已被禁用: " + username);
        }

        // 员工子账号（MEMBER）仅用于 API Key 归属审计，不允许登录管理后台
        if ("MEMBER".equals(user.getRole())) {
            throw new UsernameNotFoundException("员工账号无权登录管理后台: " + username);
        }

        return new CustomUserPrincipal(
                user.getId(),
                user.getTenantId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getRole(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()))
        );
    }
}
