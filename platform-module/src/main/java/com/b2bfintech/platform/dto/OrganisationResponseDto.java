package com.b2bfintech.platform.dto;

import com.b2bfintech.platform.model.Organisation;

import java.time.Instant;

public record OrganisationResponseDto(
        String id,
        String organisationCode,
        String organisationName,
        String tenantSchema,
        String status,
        Instant createdAt
) {
    public static OrganisationResponseDto from(Organisation organisation) {
        return new OrganisationResponseDto(
                organisation.id().toString(), organisation.organisationCode(), organisation.organisationName(),
                organisation.tenantSchema(), organisation.status(), organisation.createdAt());
    }
}
