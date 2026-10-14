package com.b2bfintech.platform.model;

import java.time.Instant;
import java.util.UUID;

/** A row in {@code platform.organisation} - the tenant registry. */
public record Organisation(
        UUID id,
        String organisationCode,
        String organisationName,
        String tenantSchema,
        String status,
        Instant createdAt
) {
}
