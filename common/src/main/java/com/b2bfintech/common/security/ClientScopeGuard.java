package com.b2bfintech.common.security;

import org.springframework.security.access.AccessDeniedException;

/**
 * Organisation-side roles (ORGANISATION_ADMIN/MAKER/CHECKER/VIEWER) can act on any client in their tenant;
 * client-side roles (CLIENT_ADMIN/CLIENT_OPERATOR/BRANCH_USER) may only ever act on their own client.
 */
public final class ClientScopeGuard {

    private ClientScopeGuard() {
    }

    public static void assertCanAccess(AuthenticatedUser principal, String clientId) {
        if (principal == null) {
            throw new AccessDeniedException("You do not have access to this client");
        }
        if (!principal.isClientUser()) {
            return; // organisation-side roles: not restricted to one client
        }
        if (clientId == null || !clientId.equals(principal.clientId())) {
            throw new AccessDeniedException("You do not have access to this client");
        }
    }
}
