package com.b2bfintech.common.security;

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
        String clientId // set only for CLIENT_ADMIN / CLIENT_OPERATOR
) {
    public boolean isPlatformUser() {
        return tenantSchema == null || tenantSchema.isBlank();
    }

    public boolean isClientUser() {
        return clientId != null && !clientId.isBlank();
    }
}
