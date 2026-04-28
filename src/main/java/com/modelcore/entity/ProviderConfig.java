package com.modelcore.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 上游供应商配置实体
 * <p>
 * 存储上游 AI 模型服务的连接配置。支持多供应商优先级路由，
 * 当主供应商不可用时自动降级到备用供应商。
 * </p>
 */
@Entity
@Table(name = "provider_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 供应商名称（如 DeepSeek、Groq） */
    @Column(nullable = false, length = 50)
    private String name;

    /** API 基础 URL */
    @Column(name = "api_url", nullable = false, length = 300)
    private String apiUrl;

    /** API 密钥 */
    @Column(name = "api_key", nullable = false, length = 300)
    private String apiKey;

    /** 优先级（数值越小优先级越高，1=主路由，2=备用路由） */
    @Column(nullable = false)
    @Builder.Default
    private Integer priority = 1;

    /** 请求超时时间（毫秒） */
    @Column(nullable = false)
    @Builder.Default
    private Integer timeout = 10000;

    /** 供应商状态：ACTIVE-启用, INACTIVE-停用 */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    /** 模型映射关系（JSON 格式，如 {"gpt-3.5-turbo":"deepseek-chat"}） */
    @Column(name = "model_mapping", length = 1000)
    private String modelMapping;

    /** 每百万输入 Token 的美元价格（行业标准单位，如 DeepSeek=0.27，GPT-4o=2.50） */
    @Column(name = "input_price_per_million", nullable = false, precision = 16, scale = 8)
    @Builder.Default
    private BigDecimal inputPricePerMillion = new BigDecimal("1.00000000");

    /** 每百万输出 Token 的美元价格（如 DeepSeek=1.10，GPT-4o=10.00） */
    @Column(name = "output_price_per_million", nullable = false, precision = 16, scale = 8)
    @Builder.Default
    private BigDecimal outputPricePerMillion = new BigDecimal("2.00000000");

    /** 换算为每个 Token 的输入价格（供计费引擎使用） */
    @Transient
    public BigDecimal getInputPricePerToken() {
        return inputPricePerMillion.divide(new BigDecimal("1000000"), 12, RoundingMode.HALF_UP);
    }

    /** 换算为每个 Token 的输出价格（供计费引擎使用） */
    @Transient
    public BigDecimal getOutputPricePerToken() {
        return outputPricePerMillion.divide(new BigDecimal("1000000"), 12, RoundingMode.HALF_UP);
    }

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
