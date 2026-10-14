package com.b2bfintech.platform.model;

import java.util.UUID;

/**
 * A row in {@code platform.biller} - a specific service provider (e.g. "Tata Power - Mumbai")
 * within one {@code biller_service_catalog} category, carrying whatever a real BBPS fetch/pay
 * call would need to route to that provider ({@code billerId}). This is the master list the
 * "Create bill" flow's provider dropdown is populated from.
 */
public record Biller(
        UUID id,
        String billerId,
        String billerName,
        String category,
        String state,
        boolean active
) {
}
