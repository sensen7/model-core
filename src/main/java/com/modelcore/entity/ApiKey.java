package com.modelcore.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * API Key 实体（下游调用凭证）
 * <p>
 * 租户管理员可创建多个 API Key，每个 Key 可独立设置月额度上限和有效期。
 * Key 值以 "sk-" 开头，全局唯一。调用 /v1/chat/completions 时通过 Bearer Token 传递。
 * </p>
 */
@Entity
@Table(name = "api_key", uniqueConstraints = {
        @UniqueConstraint(columnNames = "key_value")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属租户 ID */
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /** 关联的员工 ID（null 表示未绑定具体员工） */
    @Column(name = "user_id")
    private Long userId;

    /** Key 值（sk- 开头，48位随机字符串） */
    @Column(name = "key_value", nullable = false, length = 100)
    private String keyValue;

    /** Key 名称（便于用户区分用途） */
    @Column(nullable = false, length = 100)
    private String name;

    /** 月额度上限（美元），null 表示不限 */
    @Column(name = "monthly_limit", precision = 16, scale = 4)
    private BigDecimal monthlyLimit;

    /** Key 状态：ACTIVE-正常, DISABLED-禁用, EXPIRED-已过期 */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    /** 过期时间，null 表示永不过期 */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** 每分钟最大请求数（限流），null 表示不限速 */
    @Column(name = "rate_limit_per_minute")
    private Integer rateLimitPerMinute;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
