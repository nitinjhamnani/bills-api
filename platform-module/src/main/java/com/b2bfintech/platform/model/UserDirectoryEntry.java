package com.b2bfintech.platform.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A row in {@code platform.user_directory} - the one cross-tenant lookup table that lets the
 * login flow resolve "whose phone number is this" (and which tenant schema to route into)
 * before any tenant schema is known. See implementation plan &sect;2.
 */
public record UserDirectoryEntry(
        UUID id,
        String phoneNumber,
        UUID userId,
        String tenantSchema, // null for platform admins
        String role,
        String displayName,
        UUID clientId, // set only for CLIENT_ADMIN / CLIENT_OPERATOR
        String status,
        String email, // optional alternate login identifier; null unless set at creation
        String passwordHash, // optional; null unless the user was given a password login
        Instant createdAt
) {
    public boolean isPlatformUser() {
        return tenantSchema == null;
    }
}
