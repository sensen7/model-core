package com.modelcore.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * API Key 认证主体
 * <p>
 * 当请求通过 API Key 认证后，此对象作为 SecurityContext 中的 Principal，
 * 携带 apiKeyId、tenantId 和限流配置，供计费引擎、限流检查和日志记录使用。
 * </p>
 */
@Getter
@AllArgsConstructor
public class ApiKeyPrincipal {

    /** API Key 的数据库 ID */
    private final Long apiKeyId;

    /** 所属租户 ID */
    private final Long tenantId;

    /** Key 值（sk-xxx） */
    private final String keyValue;

    /** 每分钟最大请求数（null 表示不限速） */
    private final Integer rateLimitPerMinute;

    /** API Key 月用量上限（美元），null 表示不限 */
    private final BigDecimal monthlyLimit;
}
