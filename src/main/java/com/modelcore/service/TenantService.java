package com.modelcore.service;

import com.modelcore.entity.Tenant;
import com.modelcore.entity.User;
import com.modelcore.repository.TenantRepository;
import com.modelcore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 租户管理服务
 * <p>
 * 处理租户注册、查询、状态管理等业务逻辑。
 * 注册时自动创建租户和默认管理员账号，默认余额为 0。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenDeductionService tokenDeductionService;

    /**
     * 注册新租户（同时创建默认管理员账号）
     *
     * @param tenantName 租户名称（企业名）
     * @param username   管理员用户名
     * @param email      管理员邮箱
     * @param password   管理员密码（明文，内部加密）
     * @return 创建的租户实体
     */
    @Transactional
    public Tenant register(String tenantName, String username, String email, String password) {
        // 校验租户名唯一
        if (tenantRepository.existsByName(tenantName)) {
            throw new RuntimeException("租户名称已存在: " + tenantName);
        }

        // 校验用户名唯一
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("用户名已存在: " + username);
        }

        // 创建租户
        Tenant tenant = Tenant.builder()
                .name(tenantName)
                .contactEmail(email)
                .build();
        tenant = tenantRepository.save(tenant);

        // 创建默认管理员
        User admin = User.builder()
                .tenantId(tenant.getId())
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .role("ADMIN")
                .status("ACTIVE")
                .build();
        userRepository.save(admin);

        log.info("注册新租户: name={}, adminUser={}", tenantName, username);
        return tenant;
    }

    /**
     * 按 ID 查询租户
     */
    public Tenant getTenant(Long tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("租户不存在: " + tenantId));
    }

    /**
     * 更新租户状态
     */
    @Transactional
    public Tenant updateStatus(Long tenantId, String status) {
        Tenant tenant = getTenant(tenantId);
        tenant.setStatus(status);
        return tenantRepository.save(tenant);
    }

    /**
     * 查询所有租户（平台超管用）
     */
    public List<Tenant> listAllTenants() {
        return tenantRepository.findAll();
    }

    /**
     * 给租户充值（平台超管用）
     * 充值后同步更新 Redis 余额，确保计费引擎立即生效
     */
    @Transactional
    public Tenant rechargeTenant(Long tenantId, BigDecimal amount) {
        Tenant tenant = getTenant(tenantId);
        tenant.setBalance(tenant.getBalance().add(amount));
        Tenant saved = tenantRepository.save(tenant);
        // 同步最新余额到 Redis（计费引擎从 Redis 扣减）
        tokenDeductionService.initTenantBalance(tenantId, saved.getBalance());
        log.info("租户充值: tenantId={}, 金额={}, 新余额={}", tenantId, amount, saved.getBalance());
        return saved;
    }
}
