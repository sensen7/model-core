package com.modelcore.security;

/**
 * 多租户上下文工具类
 * <p>
 * 使用 ThreadLocal 存储当前请求的租户 ID，确保同一线程内任何位置都能获取到租户信息。
 * 在请求结束时必须调用 clear() 防止内存泄漏。
 * </p>
 */
public final class TenantContext {

    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private TenantContext() {
    }

    /** 设置当前租户 ID */
    public static void setTenantId(Long tenantId) {
        TENANT_ID.set(tenantId);
    }

    /** 获取当前租户 ID */
    public static Long getTenantId() {
        return TENANT_ID.get();
    }

    /** 设置当前用户 ID */
    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    /** 获取当前用户 ID */
    public static Long getUserId() {
        return USER_ID.get();
    }

    /** 清除上下文（请求结束时调用，防止 ThreadLocal 内存泄漏） */
    public static void clear() {
        TENANT_ID.remove();
        USER_ID.remove();
    }
}
