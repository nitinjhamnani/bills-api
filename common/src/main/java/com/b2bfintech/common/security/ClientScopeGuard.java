package com.b2bfintech.common.security;

import org.springframework.security.access.AccessDeniedException;

/**
 * Enforces portal and tenant ({@code clientId}) isolation:
 * <ul>
 *   <li>Platform users ({@link Portal#PLATFORM}) can access any corporate or vendor record as permitted by their role.</li>
 *   <li>Corporate users ({@link Portal#CORPORATE}) can only access their own corporate {@code clientId}, and never vendor endpoints.</li>
 *   <li>Vendor users ({@link Portal#VENDOR}) can only access their own vendor {@code clientId}, and never corporate endpoints.</li>
 * </ul>
 */
public final class ClientScopeGuard {

    private ClientScopeGuard() {
    }

    public static void assertCanAccess(AuthenticatedUser principal, String clientId) {
        if (principal == null) {
            throw new AccessDeniedException("You do not have access to this client");
        }
        if (principal.isPlatformUser()) {
            return;
        }
        if (clientId == null || clientId.isBlank() || !clientId.equals(principal.clientId())) {
            throw new AccessDeniedException("You do not have access to this client");
        }
    }

    public static void assertCorporateAccess(AuthenticatedUser principal, String clientId) {
        if (principal == null) {
            throw new AccessDeniedException("You do not have access to this corporate");
        }
        if (principal.isPlatformUser()) {
            return;
        }
        if (!principal.isCorporateUser()) {
            throw new AccessDeniedException("Only corporate users can access this resource");
        }
        if (clientId == null || clientId.isBlank() || !clientId.equals(principal.clientId())) {
            throw new AccessDeniedException("You do not have access to this corporate");
        }
    }

    public static void assertVendorAccess(AuthenticatedUser principal, String clientId) {
        if (principal == null) {
            throw new AccessDeniedException("You do not have access to this vendor");
        }
        if (principal.isPlatformUser()) {
            return;
        }
        if (!principal.isVendorUser()) {
            throw new AccessDeniedException("Only vendor users can access this resource");
        }
        if (clientId == null || clientId.isBlank() || !clientId.equals(principal.clientId())) {
            throw new AccessDeniedException("You do not have access to this vendor");
        }
    }
}
