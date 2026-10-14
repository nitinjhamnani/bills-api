package com.b2bfintech.common.tenant;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/**
 * Tells Hibernate which tenant schema the current thread belongs to, sourced from
 * {@link TenantContext}. Falls back to {@link TenantContext#DEFAULT_SCHEMA} for threads that
 * never had a tenant set (platform-admin-only requests, background startup, etc.).
 */
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    @Override
    public String resolveCurrentTenantIdentifier() {
        return TenantContext.getCurrentTenant();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
