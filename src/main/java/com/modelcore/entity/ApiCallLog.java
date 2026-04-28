package com.modelcore.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * API 调用日志实体
 * <p>
 * 记录每一次 API 调用的详细信息，包括 Token 用量、费用、供应商、耗时等。
 * 用于审计追溯和用量统计。按租户隔离，租户之间互不可见。
 * </p>
 */
@Entity
@Table(name = "api_call_log", indexes = {
        @Index(name = "idx_call_log_tenant", columnList = "tenant_id"),
        @Index(name = "idx_call_log_api_key", columnList = "api_key_id"),
        @Index(name = "idx_call_log_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiCallLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属租户 ID */
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /** 使用的 API Key ID */
    @Column(name = "api_key_id", nullable = false)
    private Long apiKeyId;

    /** 请求的模型名称 */
    @Column(length = 100)
    private String model;

    /** 输入 Token 数 */
    @Column(name = "prompt_tokens")
    @Builder.Default
    private Integer promptTokens = 0;

    /** 输出 Token 数 */
    @Column(name = "completion_tokens")
    @Builder.Default
    private Integer completionTokens = 0;

    /** 总 Token 数 */
    @Column(name = "total_tokens")
    @Builder.Default
    private Integer totalTokens = 0;

    /** 本次调用费用（美元），精度 4 位小数 */
    @Column(nullable = false, precision = 16, scale = 4)
    @Builder.Default
    private BigDecimal cost = BigDecimal.ZERO;

    /** 实际使用的供应商名称（用于追踪降级情况） */
    @Column(length = 50)
    private String provider;

    /** 请求耗时（毫秒） */
    @Column
    private Long duration;

    /** 调用状态：SUCCESS-成功, FAILED-失败, FALLBACK-降级成功 */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "SUCCESS";

    /** 请求体摘要（截取前 500 字符，用于审计） */
    @Column(name = "request_body", length = 500)
    private String requestBody;

    /** 响应体摘要（截取前 1000 字符，用于审计追溯） */
    @Column(name = "response_body", length = 1000)
    private String responseBody;

    /** 错误信息（失败时记录） */
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
