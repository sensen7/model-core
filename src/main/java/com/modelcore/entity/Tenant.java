package com.modelcore.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 租户实体（企业客户）
 * <p>
 * SaaS 多租户体系的顶层实体，每个租户代表一个企业客户。
 * 租户下可拥有多个子账号（User）和 API Key。
 * </p>
 */
@Entity
@Table(name = "tenant")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 租户名称（企业名） */
    @Column(nullable = false, length = 100)
    private String name;

    /** 联系邮箱 */
    @Column(name = "contact_email", length = 200)
    private String contactEmail;

    /** 账户余额（美元），精度 4 位小数 */
    @Column(nullable = false, precision = 16, scale = 4)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    /** 租户状态：ACTIVE-正常, SUSPENDED-停用 */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    /**
     * 余额预警 Webhook 地址（可选）
     * 当任意 API Key 余额低于阈值时，向此 URL 发送 POST 通知。
     * 格式兼容 Slack / Discord / 企业微信机器人。
     */
    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
