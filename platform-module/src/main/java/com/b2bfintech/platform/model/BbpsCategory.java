package com.b2bfintech.platform.model;

import java.time.Instant;
import java.util.UUID;

public record BbpsCategory(
        UUID id,
        String providerCode,
        String categoryId,
        String name,
        String categoryGroup,
        boolean active,
        Instant syncedAt
) {
}
