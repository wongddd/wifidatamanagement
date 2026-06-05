package com.alenwifidata.core.tenant;

/**
 * 租户上下文（ThreadLocal 传递）
 */
public class TenantContext {

    private static final ThreadLocal<Long> TENANT_HOLDER = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(Long tenantId) {
        TENANT_HOLDER.set(tenantId);
    }

    public static Long get() {
        return TENANT_HOLDER.get();
    }

    public static void clear() {
        TENANT_HOLDER.remove();
    }
}
