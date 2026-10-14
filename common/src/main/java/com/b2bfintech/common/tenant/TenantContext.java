package com.b2bfintech.common.tenant;

/**
 * Holds the current request's tenant schema name (e.g. "tenant_acme") for the lifetime of the
 * thread handling that request. Set by {@code JwtAuthenticationFilter} from the JWT's
 * {@code tenantSchema} claim, or explicitly by services (e.g. organisation provisioning) that need to
 * write into a specific tenant schema outside of an HTTP request.
 *
 * <p>Platform-admin requests (no tenant) leave this unset; {@link #DEFAULT_SCHEMA} is used as a
 * safe fallback for Hibernate's {@code CurrentTenantIdentifierResolver} so schema resolution never
 * throws even when no tenant applies to the current thread.
 */
public final class TenantContext {

    public static final String DEFAULT_SCHEMA = "public";

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setCurrentTenant(String tenantSchema) {
        CURRENT_TENANT.set(tenantSchema);
    }

    public static String getCurrentTenant() {
        String tenant = CURRENT_TENANT.get();
        return tenant != null ? tenant : DEFAULT_SCHEMA;
    }

    public static boolean hasTenant() {
        return CURRENT_TENANT.get() != null;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
