package com.b2bfintech.common.security;

import java.util.UUID;

/**
 * The authenticated principal put on {@code SecurityContextHolder} by {@link JwtAuthenticationFilter}.
 * Available in controllers via {@code @AuthenticationPrincipal AuthenticatedUser user}.
 */
public record AuthenticatedUser(
        String userId,
        String phoneNumber,
        String tenantSchema,
        String role,
        String displayName,
        String clientId,
        String userCode,
        String branchId,
        UUID sessionId,
        Portal portal,
        String userType
) {
    /** Backward-compatible constructor for unit tests that do not exercise HTTP sessions. */
    public AuthenticatedUser(
            String userId,
            String phoneNumber,
            String tenantSchema,
            String role,
            String displayName,
            String clientId,
            String userCode,
            String branchId
    ) {
        this(
                userId,
                phoneNumber,
                tenantSchema,
                role,
                displayName,
                clientId,
                userCode,
                branchId,
                null,
                inferPortal(role, clientId),
                role
        );
    }

    private static Portal inferPortal(String role, String clientId) {
        if (role != null && role.startsWith("VENDOR_")) {
            return Portal.VENDOR;
        }
        if (role != null && (role.startsWith("CORP_") || "BRANCH_USER".equals(role))) {
            return Portal.CORPORATE;
        }
        if (clientId != null && !clientId.isBlank()) {
            return clientId.startsWith("VD") ? Portal.VENDOR : Portal.CORPORATE;
        }
        return Portal.PLATFORM;
    }

    public boolean isPlatformUser() {
        return portal == Portal.PLATFORM;
    }

    public boolean isCorporateUser() {
        return portal == Portal.CORPORATE;
    }

    public boolean isVendorUser() {
        return portal == Portal.VENDOR;
    }

    public boolean isClientUser() {
        return portal == Portal.CORPORATE || portal == Portal.VENDOR;
    }
}
