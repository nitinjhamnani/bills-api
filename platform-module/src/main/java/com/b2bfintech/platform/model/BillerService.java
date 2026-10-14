package com.b2bfintech.platform.model;

import java.util.UUID;

/** A row in {@code platform.biller_service_catalog} - the master list organisations pick a subset from. */
public record BillerService(
        UUID id,
        String serviceCode,
        String serviceName,
        String category,
        boolean active
) {
}
