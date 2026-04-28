package com.modelcore.service;

import com.modelcore.entity.User;
import com.modelcore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户管理服务
 * <p>
 * 处理用户的子账号管理（创建、禁用、查询）。
 * 登录认证由 Spring Security 的 CustomUserDetailsService 处理，此处不重复实现。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 创建子账号（租户管理员操作）
     *
     * @param tenantId 租户 ID
     * @param username 用户名
     * @param email    邮箱
     * @param password 密码（明文）
     * @param role     角色（ADMIN/MEMBER）
     * @return 创建的用户实体
     */
    @Transactional
    public User createUser(Long tenantId, String username, String email, String password, String role) {
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("用户名已存在: " + username);
        }

        User user = User.builder()
                .tenantId(tenantId)
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .role(role)
                .status("ACTIVE")
                .build();

        user = userRepository.save(user);
        log.info("创建子账号: tenantId={}, username={}, role={}", tenantId, username, role);
        return user;
    }

    /**
     * 禁用用户
     */
    @Transactional
    public User disableUser(Long userId, Long tenantId) {
        User user = getUserWithTenantCheck(userId, tenantId);
        user.setStatus("DISABLED");
        return userRepository.save(user);
    }

    /**
     * 启用用户
     */
    @Transactional
    public User enableUser(Long userId, Long tenantId) {
        User user = getUserWithTenantCheck(userId, tenantId);
        user.setStatus("ACTIVE");
        return userRepository.save(user);
    }

    /**
     * 查询租户下所有用户
     */
    public List<User> listUsers(Long tenantId) {
        return userRepository.findByTenantId(tenantId);
    }

    /**
     * 按 ID 查询用户（含租户校验）
     */
    public User getUser(Long userId, Long tenantId) {
        return getUserWithTenantCheck(userId, tenantId);
    }

    private User getUserWithTenantCheck(Long userId, Long tenantId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在: " + userId));
        if (!user.getTenantId().equals(tenantId)) {
            throw new RuntimeException("无权访问此用户");
        }
        return user;
    }
}
