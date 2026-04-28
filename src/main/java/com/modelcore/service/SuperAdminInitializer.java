package com.modelcore.service;

import com.modelcore.entity.User;
import com.modelcore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 超级管理员初始化器
 * <p>
 * 应用启动时检查是否存在超管账号，不存在则自动创建。
 * 默认用户名: admin，密码: admin123，首次登录后建议修改。
 * </p>
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class SuperAdminInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.findByUsername("admin").isEmpty()) {
            User superAdmin = User.builder()
                    .tenantId(null)
                    .username("admin")
                    .email("admin@modelcore.local")
                    .passwordHash(passwordEncoder.encode("admin123"))
                    .role("SUPER_ADMIN")
                    .status("ACTIVE")
                    .build();
            userRepository.save(superAdmin);
            log.info("已创建默认超级管理员账号: admin / admin123");
        }
    }
}
