package com.modelcore.service;

import com.modelcore.entity.ApiKey;
import com.modelcore.repository.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

/**
 * API Key 管理服务
 * <p>
 * 提供 Key 的创建（自动生成 sk- 前缀随机串）、禁用/启用、设置月额度、查询等功能。
 * 所有操作均需校验租户隔离，确保跨租户不可访问。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;

    /** 安全随机数生成器 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** Key 字符集（去除易混淆字符） */
    private static final String KEY_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz0123456789";

    /** Key 随机部分长度 */
    private static final int KEY_LENGTH = 45;

    /**
     * 创建 API Key
     *
     * @param tenantId     租户 ID
     * @param userId       关联的员工 ID（null 表示不绑定具体员工）
     * @param name         Key 名称
     * @param monthlyLimit 月用量上限（美元），null 表示不限
     * @param expiresAt    过期时间（null 表示永不过期）
     * @return 创建的 ApiKey 实体
     */
    @Transactional
    public ApiKey createKey(Long tenantId, Long userId, String name,
                            BigDecimal monthlyLimit, LocalDateTime expiresAt) {
        String keyValue = generateUniqueKey();

        ApiKey apiKey = ApiKey.builder()
                .tenantId(tenantId)
                .userId(userId)
                .keyValue(keyValue)
                .name(name)
                .monthlyLimit(monthlyLimit)
                .expiresAt(expiresAt)
                .status("ACTIVE")
                .build();

        apiKey = apiKeyRepository.save(apiKey);
        log.info("创建 API Key: tenantId={}, userId={}, name={}, keyId={}", tenantId, userId, name, apiKey.getId());
        return apiKey;
    }

    /**
     * 禁用 API Key
     */
    @Transactional
    public ApiKey disableKey(Long keyId, Long tenantId) {
        ApiKey apiKey = getKeyWithTenantCheck(keyId, tenantId);
        apiKey.setStatus("DISABLED");
        return apiKeyRepository.save(apiKey);
    }

    /**
     * 启用 API Key
     */
    @Transactional
    public ApiKey enableKey(Long keyId, Long tenantId) {
        ApiKey apiKey = getKeyWithTenantCheck(keyId, tenantId);
        apiKey.setStatus("ACTIVE");
        return apiKeyRepository.save(apiKey);
    }

    /**
     * 更新月额度上限
     */
    @Transactional
    public ApiKey updateMonthlyLimit(Long keyId, Long tenantId, BigDecimal monthlyLimit) {
        ApiKey apiKey = getKeyWithTenantCheck(keyId, tenantId);
        apiKey.setMonthlyLimit(monthlyLimit);
        return apiKeyRepository.save(apiKey);
    }

    /**
     * 查询租户下所有 Key
     */
    public List<ApiKey> listKeys(Long tenantId) {
        return apiKeyRepository.findByTenantId(tenantId);
    }

    /**
     * 按 ID 查询 Key（含租户校验）
     */
    public ApiKey getKey(Long keyId, Long tenantId) {
        return getKeyWithTenantCheck(keyId, tenantId);
    }

    /**
     * 统计租户下活跃 Key 数量
     */
    public long countActiveKeys(Long tenantId) {
        return apiKeyRepository.countByTenantIdAndStatus(tenantId, "ACTIVE");
    }

    /**
     * 生成全局唯一的 sk- 前缀 Key
     */
    private String generateUniqueKey() {
        String keyValue;
        do {
            keyValue = "sk-" + randomString(KEY_LENGTH);
        } while (apiKeyRepository.existsByKeyValue(keyValue));
        return keyValue;
    }

    private String randomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(KEY_CHARS.charAt(SECURE_RANDOM.nextInt(KEY_CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * 获取 Key 并校验租户归属（防止跨租户访问）
     */
    private ApiKey getKeyWithTenantCheck(Long keyId, Long tenantId) {
        ApiKey apiKey = apiKeyRepository.findById(keyId)
                .orElseThrow(() -> new RuntimeException("API Key 不存在: " + keyId));
        if (!apiKey.getTenantId().equals(tenantId)) {
            throw new RuntimeException("无权访问此 API Key");
        }
        return apiKey;
    }
}
