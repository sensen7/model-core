package com.modelcore.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 用户实体（租户下的子账号）
 * <p>
 * 每个用户归属于一个租户，角色分为 SUPER_ADMIN（平台超管）、ADMIN（租户管理员）和 MEMBER（普通成员）。
 * SUPER_ADMIN 不属于任何租户（tenantId 为 null），拥有全平台管理权限。
 * 租户管理员拥有 Key 管理、日志查看、子账号管理等权限。
 * </p>
 */
@Entity
@Table(name = "user", uniqueConstraints = {
        @UniqueConstraint(columnNames = "username")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属租户 ID（SUPER_ADMIN 为 null） */
    @Column(name = "tenant_id")
    private Long tenantId;

    /** 用户名（全局唯一） */
    @Column(nullable = false, length = 50)
    private String username;

    /** 邮箱 */
    @Column(length = 200)
    private String email;

    /** 密码哈希（BCrypt） */
    @Column(name = "password_hash", nullable = false, length = 200)
    private String passwordHash;

    /** 角色：SUPER_ADMIN-平台超管, ADMIN-租户管理员, MEMBER-普通成员 */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String role = "ADMIN";

    /** 用户状态：ACTIVE-正常, DISABLED-禁用 */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
