package com.modelcore.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Token 计费扣减服务
 * <p>
 * 余额统一归属于租户，所有 API Key 共享租户余额。
 * 基于 JDK 21 虚拟线程，使用同步 StringRedisTemplate，无需响应式编程。
 * Redis Key 结构：
 *   - tenant:balance:{tenantId}     租户总余额（单位：分，× 10000）
 *   - usage:key:{keyId}:{yyyy-MM}   API Key 本月已用金额（单位：分），月末自动过期
 * </p>
 */
@Slf4j
@Service
public class TokenDeductionService {

    /** 租户余额 Redis Key 前缀 */
    private static final String TENANT_BALANCE_KEY_PREFIX = "tenant:balance:";

    /** API Key 月用量 Redis Key 前缀，格式：usage:key:{keyId}:{yyyy-MM} */
    private static final String KEY_USAGE_KEY_PREFIX = "usage:key:";

    /** 精度倍数（4 位小数） */
    private static final long PRECISION_MULTIPLIER = 10000L;

    /** 余额预警阈值（美元）：低于此值时触发 Webhook */
    private static final BigDecimal LOW_BALANCE_THRESHOLD = new BigDecimal("1.00");

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> deductScript;
    private final RedisScript<Long> confirmScript;
    private final WebhookService webhookService;

    public TokenDeductionService(StringRedisTemplate redisTemplate, WebhookService webhookService) {
        this.redisTemplate = redisTemplate;
        this.webhookService = webhookService;
        this.deductScript = RedisScript.of(new ClassPathResource("lua/deduct_balance.lua"), Long.class);
        this.confirmScript = RedisScript.of(new ClassPathResource("lua/confirm_deduction.lua"), Long.class);
    }

    /**
     * 预扣费用（请求进入时调用）
     *
     * @param tenantId      租户 ID（余额从租户扣）
     * @param estimatedCost 预估费用（美元）
     * @return 扣减后的剩余余额（美元），余额不足时返回负数
     */
    public BigDecimal preDeduct(Long tenantId, BigDecimal estimatedCost) {
        String key = TENANT_BALANCE_KEY_PREFIX + tenantId;
        long amount = toStorageValue(estimatedCost);

        Long result = redisTemplate.execute(deductScript, List.of(key), String.valueOf(amount));
        if (result == null || result == -2L) {
            log.warn("租户余额 Key 不存在，tenantId={}", tenantId);
            return BigDecimal.valueOf(-2);
        }
        if (result == -1L) {
            log.warn("租户余额不足，tenantId={}, 预估费用={}", tenantId, estimatedCost);
            return BigDecimal.valueOf(-1);
        }
        return fromStorageValue(result);
    }

    /**
     * 二次确认扣费（响应结束后调用）
     *
     * @param tenantId      租户 ID
     * @param estimatedCost 预估费用（美元）
     * @param actualCost    实际费用（美元）
     * @param apiKeyId      API Key ID（用于触发余额预警 Webhook）
     * @return 调整后的最新余额（美元）
     */
    public BigDecimal confirmDeduction(Long tenantId, BigDecimal estimatedCost,
                                       BigDecimal actualCost, Long apiKeyId) {
        String key = TENANT_BALANCE_KEY_PREFIX + tenantId;
        long estimated = toStorageValue(estimatedCost);
        long actual = toStorageValue(actualCost);

        Long result = redisTemplate.execute(confirmScript, List.of(key),
                String.valueOf(estimated), String.valueOf(actual));
        if (result == null || result == -2L) {
            log.error("二次确认时余额 Key 不存在，tenantId={}", tenantId);
            return BigDecimal.valueOf(-2);
        }
        if (result == -1L) {
            log.warn("二次确认时余额不足，已扣至 0，tenantId={}", tenantId);
            return BigDecimal.ZERO;
        }
        BigDecimal remaining = fromStorageValue(result);
        // 余额预警：低于阈值时触发 Webhook 推送（异步，不阻塞主流程）
        if (remaining.compareTo(LOW_BALANCE_THRESHOLD) <= 0) {
            log.warn("余额预警：租户 [{}] 剩余余额 ${} 已低于阈值 ${}，请及时充值",
                    tenantId, remaining, LOW_BALANCE_THRESHOLD);
            webhookService.triggerLowBalanceAlert(apiKeyId, remaining);
        }
        return remaining;
    }

    /**
     * 退款（请求失败时全额退还预扣费用）
     *
     * @param tenantId 租户 ID
     * @param amount   退还金额（美元）
     * @return 退还后的余额
     */
    public BigDecimal refund(Long tenantId, BigDecimal amount) {
        String key = TENANT_BALANCE_KEY_PREFIX + tenantId;
        long refundAmount = toStorageValue(amount);
        Long newValue = redisTemplate.opsForValue().increment(key, refundAmount);
        return newValue != null ? fromStorageValue(newValue) : BigDecimal.ZERO;
    }

    /**
     * 初始化或更新租户余额到 Redis（充值时调用）
     *
     * @param tenantId 租户 ID
     * @param balance  最新余额（美元）
     */
    public void initTenantBalance(Long tenantId, BigDecimal balance) {
        String key = TENANT_BALANCE_KEY_PREFIX + tenantId;
        long value = toStorageValue(balance);
        redisTemplate.opsForValue().set(key, String.valueOf(value));
    }

    /**
     * 从 Redis 获取租户当前余额
     *
     * @param tenantId 租户 ID
     * @return 当前余额（美元）
     */
    public BigDecimal getTenantBalance(Long tenantId) {
        String key = TENANT_BALANCE_KEY_PREFIX + tenantId;
        String val = redisTemplate.opsForValue().get(key);
        if (val == null) return BigDecimal.ZERO;
        return fromStorageValue(Long.parseLong(val));
    }

    /**
     * 检查 API Key 本月用量是否已超出上限
     *
     * @param apiKeyId     API Key ID
     * @param monthlyLimit 月用量上限（美元），null 表示不限
     * @return true=允许，false=已超限
     */
    public boolean checkMonthlyLimit(Long apiKeyId, BigDecimal monthlyLimit) {
        if (monthlyLimit == null || monthlyLimit.compareTo(BigDecimal.ZERO) <= 0) {
            // 未设置上限，直接放行
            return true;
        }
        String key = monthlyUsageKey(apiKeyId);
        String val = redisTemplate.opsForValue().get(key);
        if (val == null) return true; // Key 不存在说明本月还未使用，放行
        BigDecimal used = fromStorageValue(Long.parseLong(val));
        return used.compareTo(monthlyLimit) < 0;
    }

    /**
     * 累加 API Key 本月用量（确认扣费后调用）
     *
     * @param apiKeyId   API Key ID
     * @param actualCost 本次实际费用（美元）
     */
    public void incrementMonthlyUsage(Long apiKeyId, BigDecimal actualCost) {
        if (actualCost == null || actualCost.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String key = monthlyUsageKey(apiKeyId);
        long amount = toStorageValue(actualCost);
        redisTemplate.opsForValue().increment(key, amount);
        // 设置 35 天 TTL（覆盖整个月，月末自动过期）
        redisTemplate.expire(key, Duration.ofDays(35));
    }

    /**
     * 构造 API Key 月用量 Redis Key，格式：usage:key:{keyId}:{yyyy-MM}
     */
    private String monthlyUsageKey(Long apiKeyId) {
        String yearMonth = YearMonth.from(LocalDate.now()).toString(); // 如 "2026-04"
        return KEY_USAGE_KEY_PREFIX + apiKeyId + ":" + yearMonth;
    }

    /** 金额转存储值（× 10000） */
    private long toStorageValue(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(PRECISION_MULTIPLIER))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    /** 存储值转金额（÷ 10000） */
    private BigDecimal fromStorageValue(Long value) {
        return BigDecimal.valueOf(value)
                .divide(BigDecimal.valueOf(PRECISION_MULTIPLIER), 4, RoundingMode.HALF_UP);
    }
}
