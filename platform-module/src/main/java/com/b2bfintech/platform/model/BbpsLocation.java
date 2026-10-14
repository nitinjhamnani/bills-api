package com.b2bfintech.platform.model;

import java.time.Instant;
import java.util.UUID;

public record BbpsLocation(
        UUID id,
        String providerCode,
        String locationId,
        String name,
        String abbreviation,
        boolean active,
        Instant syncedAt
) {
}
