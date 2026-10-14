package com.b2bfintech.common.security;

import com.b2bfintech.common.exception.UnauthorizedException;

import java.util.UUID;

/**
 * Organisation-side roles (ORGANISATION_ADMIN/MAKER/CHECKER/VIEWER) can act on any client in their tenant;
 * client-side roles (CLIENT_ADMIN/CLIENT_OPERATOR) may only ever act on their own client, even
 * though the tenant-schema routing alone wouldn't stop them reaching another client's data.
 */
public final class ClientScopeGuard {

    private ClientScopeGuard() {
    }

    public static void assertCanAccess(AuthenticatedUser principal, UUID clientId) {
        if (!principal.isClientUser()) {
            return; // organisation-side roles: not restricted to one client
        }
        if (!principal.clientId().equals(clientId.toString())) {
            throw new UnauthorizedException("You do not have access to this client");
        }
    }
}
