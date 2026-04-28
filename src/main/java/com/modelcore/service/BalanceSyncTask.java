package com.modelcore.service;

import com.modelcore.entity.Tenant;
import com.modelcore.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 租户余额同步定时任务
 * <p>
 * 职责一（启动时）：将数据库中所有租户的余额加载到 Redis，确保重启后计费引擎立即可用。
 * 职责二（定时）：每 5 分钟将 Redis 中的实时余额同步回 MariaDB，防止 Redis 宕机导致数据丢失。
 * 基于 JDK 21 虚拟线程，使用同步 StringRedisTemplate。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BalanceSyncTask {

    private static final String TENANT_BALANCE_KEY_PREFIX = "tenant:balance:";
    private static final long PRECISION_MULTIPLIER = 10000L;

    private final StringRedisTemplate redisTemplate;
    private final TenantRepository tenantRepository;

    /**
     * 启动后 10 秒执行一次：将所有租户余额从 DB 加载到 Redis
     * 此后每 5 分钟执行一次：将 Redis 余额同步回 DB
     */
    @Scheduled(fixedRate = 300000, initialDelay = 10000)
    public void syncBalancesToDatabase() {
        log.debug("开始同步租户余额（Redis ↔ DB）...");

        List<Tenant> activeTenants = tenantRepository.findByStatus("ACTIVE");

        for (Tenant tenant : activeTenants) {
            try {
                String redisKey = TENANT_BALANCE_KEY_PREFIX + tenant.getId();
                String value = redisTemplate.opsForValue().get(redisKey);

                if (value == null) {
                    // Redis 中不存在（重启后首次），从 DB 初始化到 Redis
                    long stored = tenant.getBalance()
                            .multiply(BigDecimal.valueOf(PRECISION_MULTIPLIER))
                            .setScale(0, RoundingMode.HALF_UP)
                            .longValue();
                    redisTemplate.opsForValue().set(redisKey, String.valueOf(stored));
                    log.info("初始化租户余额到 Redis: tenantId={}, 余额={}", tenant.getId(), tenant.getBalance());
                } else {
                    // Redis 中已有值，同步回 DB
                    BigDecimal redisBalance = BigDecimal.valueOf(Long.parseLong(value))
                            .divide(BigDecimal.valueOf(PRECISION_MULTIPLIER), 4, RoundingMode.HALF_UP);

                    if (redisBalance.compareTo(tenant.getBalance()) != 0) {
                        tenant.setBalance(redisBalance);
                        tenantRepository.save(tenant);
                        log.debug("同步租户余额到 DB: tenantId={}, 新余额={}", tenant.getId(), redisBalance);
                    }
                }
            } catch (Exception e) {
                log.error("同步租户余额失败: tenantId={}", tenant.getId(), e);
            }
        }

        log.debug("租户余额同步完成，共处理 {} 个租户", activeTenants.size());
    }
}
