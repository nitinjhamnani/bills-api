package com.b2bfintech.app.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class PlatformOrganisationDtos {

    private PlatformOrganisationDtos() {}

    public record OrganisationUserDto(
            String userId,
            String displayName,
            String phoneNumber,
            String email,
            String role,
            String status,
            Instant createdAt
    ) {}

    public record OrganisationUserDetailDto(
            String userId,
            String displayName,
            String phoneNumber,
            String email,
            String role,
            String status,
            Instant createdAt,
            String organisationId,
            String organisationName,
            String clientId,
            String clientName,
            String clientCode
    ) {}

    public record CreateAdminRequest(
            String phoneNumber,
            String fullName,
            String email,
            String password
    ) {}

    public record SetPasswordRequest(String password) {}

    public record OrganisationClientDto(
            String id,
            String clientName,
            String clientCode,
            String clientType,
            String status,
            String contactPhone,
            Instant createdAt,
            OrganisationUserDto admin
    ) {}

    public record OrganisationDetailDto(
            String id,
            String organisationCode,
            String organisationName,
            String tenantSchema,
            String status,
            Instant createdAt,
            OrganisationUserDto admin,
            List<OrganisationUserDto> staff,
            List<OrganisationClientDto> clients,
            long clientCount,
            long staffCount,
            long billsPendingPickup,
            long billsInProcess,
            long billsPaid,
            BigDecimal totalGmv
    ) {}

    public record UpdateStatusRequest(String status) {}
}
