package com.b2bfintech.app.service;

import com.b2bfintech.app.controller.PlatformOrganisationDtos.CreateAdminRequest;
import com.b2bfintech.app.controller.PlatformOrganisationDtos.OrganisationClientDto;
import com.b2bfintech.app.controller.PlatformOrganisationDtos.OrganisationDetailDto;
import com.b2bfintech.app.controller.PlatformOrganisationDtos.OrganisationUserDetailDto;
import com.b2bfintech.app.controller.PlatformOrganisationDtos.OrganisationUserDto;
import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.billing.repository.BillPaymentRepository;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.common.tenant.TenantContext;
import com.b2bfintech.organisation.entity.AppUser;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.repository.ClientRepository;
import com.b2bfintech.organisation.service.TenantUserService;
import com.b2bfintech.platform.model.Organisation;
import com.b2bfintech.platform.model.UserDirectoryEntry;
import com.b2bfintech.platform.repository.OrganisationRepository;
import com.b2bfintech.platform.repository.PlatformAuditRepository;
import com.b2bfintech.platform.repository.UserDirectoryRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class PlatformOrganisationService {

    private final OrganisationRepository organisationRepository;
    private final UserDirectoryRepository userDirectoryRepository;
    private final ClientRepository clientRepository;
    private final BillPaymentRepository billPaymentRepository;
    private final PlatformAuditRepository platformAuditRepository;
    private final TenantUserService tenantUserService;
    private final TenantUserProvisioningService provisioningService;

    public PlatformOrganisationService(
            OrganisationRepository organisationRepository,
            UserDirectoryRepository userDirectoryRepository,
            ClientRepository clientRepository,
            BillPaymentRepository billPaymentRepository,
            PlatformAuditRepository platformAuditRepository,
            TenantUserService tenantUserService,
            TenantUserProvisioningService provisioningService) {
        this.organisationRepository = organisationRepository;
        this.userDirectoryRepository = userDirectoryRepository;
        this.clientRepository = clientRepository;
        this.billPaymentRepository = billPaymentRepository;
        this.platformAuditRepository = platformAuditRepository;
        this.tenantUserService = tenantUserService;
        this.provisioningService = provisioningService;
    }

    public OrganisationDetailDto getDetail(UUID id) {
        Organisation organisation = organisationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organisation not found"));
        List<UserDirectoryEntry> directory = userDirectoryRepository.findByTenantSchema(organisation.tenantSchema());

        List<UserDirectoryEntry> staffEntries = directory.stream()
                .filter(entry -> entry.clientId() == null)
                .toList();
        OrganisationUserDto admin = staffEntries.stream()
                .filter(entry -> "ORGANISATION_ADMIN".equals(entry.role()))
                .min(Comparator.comparing(UserDirectoryEntry::createdAt))
                .map(this::toUser)
                .orElse(null);

        TenantContext.setCurrentTenant(organisation.tenantSchema());
        try {
            List<OrganisationClientDto> clients = clientRepository.findAll().stream()
                    .sorted(Comparator.comparing(Client::getCreatedAt).reversed())
                    .map(client -> toClient(client, directory))
                    .toList();
            return new OrganisationDetailDto(
                    organisation.id().toString(),
                    organisation.organisationCode(),
                    organisation.organisationName(),
                    organisation.tenantSchema(),
                    organisation.status(),
                    organisation.createdAt(),
                    admin,
                    staffEntries.stream().map(this::toUser).toList(),
                    clients,
                    clients.size(),
                    staffEntries.size(),
                    billPaymentRepository.countByOrgStatus(BillPayment.OrgStatus.PENDING),
                    billPaymentRepository.countByOrgStatus(BillPayment.OrgStatus.IN_PROCESS),
                    billPaymentRepository.countByOrgStatus(BillPayment.OrgStatus.PAID),
                    billPaymentRepository.sumAmountByOrgStatus(BillPayment.OrgStatus.PAID));
        } finally {
            TenantContext.clear();
        }
    }

    public OrganisationDetailDto updateStatus(UUID id, String status) {
        if (!"ACTIVE".equals(status) && !"SUSPENDED".equals(status)) {
            throw new BadRequestException("status must be ACTIVE or SUSPENDED");
        }
        Organisation organisation = organisationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organisation not found"));
        organisationRepository.updateStatus(id, status);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        String actor = (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user)
                ? user.phoneNumber()
                : "SYSTEM";
        platformAuditRepository.record(
                actor,
                "PLATFORM_ADMIN",
                "ACTIVE".equals(status) ? "ORGANISATION_REACTIVATED" : "ORGANISATION_SUSPENDED",
                "Organisation",
                organisation.organisationCode(),
                organisation.organisationName() + " set to " + status);

        return getDetail(id);
    }

    public OrganisationUserDetailDto getUser(UUID organisationId, UUID userId) {
        Organisation organisation = requireOrganisation(organisationId);
        UserDirectoryEntry entry = requireDirectoryUser(organisation, userId);
        TenantContext.setCurrentTenant(organisation.tenantSchema());
        try {
            AppUser user = tenantUserService.getById(userId);
            String clientId = entry.clientId() == null ? null : entry.clientId().toString();
            Client entryClient = entry.clientId() == null ? null : clientRepository.findById(entry.clientId()).orElse(null);
            String clientName = entryClient != null ? entryClient.getClientName() : null;
            String clientCode = entryClient != null ? entryClient.getClientCode() : null;
            return toUserDetail(organisation, entry, user.getEmail(), clientId, clientName, clientCode);
        } finally {
            TenantContext.clear();
        }
    }

    public OrganisationUserDetailDto createAdmin(UUID organisationId, CreateAdminRequest request) {
        if (request.fullName() == null || request.fullName().isBlank()) {
            throw new BadRequestException("fullName is required");
        }
        if (request.phoneNumber() == null || !request.phoneNumber().matches("\\d{10}")) {
            throw new BadRequestException("phoneNumber must be a 10-digit mobile number");
        }
        if (userDirectoryRepository.existsByPhoneNumber(request.phoneNumber())) {
            throw new BadRequestException("Phone number " + request.phoneNumber() + " is already registered");
        }
        Organisation organisation = requireOrganisation(organisationId);
        TenantContext.setCurrentTenant(organisation.tenantSchema());
        try {
            AppUser user = provisioningService.createOrganisationUser(
                    request.phoneNumber(),
                    request.fullName().trim(),
                    "ORGANISATION_ADMIN",
                    request.email(),
                    request.password());
            audit(organisation, "ORGANISATION_ADMIN_ADDED", user.getId().toString(),
                    request.fullName().trim() + " · " + request.phoneNumber());
            UserDirectoryEntry entry = requireDirectoryUser(organisation, user.getId());
            return toUserDetail(organisation, entry, user.getEmail(), null, null, null);
        } finally {
            TenantContext.clear();
        }
    }

    public OrganisationUserDetailDto updateUserStatus(UUID organisationId, UUID userId, String status) {
        if (!"ACTIVE".equals(status) && !"SUSPENDED".equals(status)) {
            throw new BadRequestException("status must be ACTIVE or SUSPENDED");
        }
        Organisation organisation = requireOrganisation(organisationId);
        UserDirectoryEntry entry = requireDirectoryUser(organisation, userId);
        if ("SUSPENDED".equals(status) && isLastActiveAdmin(organisation, entry)) {
            throw new BadRequestException("Add another organisation admin before suspending this one");
        }
        TenantContext.setCurrentTenant(organisation.tenantSchema());
        try {
            if ("SUSPENDED".equals(status)) provisioningService.suspendUser(userId);
            else provisioningService.reactivateUser(userId);
            audit(organisation,
                    "ACTIVE".equals(status) ? "ORGANISATION_USER_REACTIVATED" : "ORGANISATION_USER_SUSPENDED",
                    userId.toString(),
                    entry.displayName() + " set to " + status);
            return getUser(organisationId, userId);
        } finally {
            TenantContext.clear();
        }
    }

    public void setUserPassword(UUID organisationId, UUID userId, String password) {
        if (password == null || password.length() < 6 || password.length() > 72) {
            throw new BadRequestException("password must be 6-72 characters");
        }
        Organisation organisation = requireOrganisation(organisationId);
        requireDirectoryUser(organisation, userId);
        TenantContext.setCurrentTenant(organisation.tenantSchema());
        try {
            provisioningService.setUserPassword(userId, password);
            audit(organisation, "ORGANISATION_USER_PASSWORD_SET", userId.toString(), "Password updated");
        } finally {
            TenantContext.clear();
        }
    }

    private Organisation requireOrganisation(UUID id) {
        return organisationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organisation not found"));
    }

    private UserDirectoryEntry requireDirectoryUser(Organisation organisation, UUID userId) {
        return userDirectoryRepository.findByTenantSchema(organisation.tenantSchema()).stream()
                .filter(entry -> entry.userId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("User not found in this organisation"));
    }

    private boolean isLastActiveAdmin(Organisation organisation, UserDirectoryEntry target) {
        if (!"ORGANISATION_ADMIN".equals(target.role()) || target.clientId() != null) return false;
        return userDirectoryRepository.findByTenantSchema(organisation.tenantSchema()).stream()
                .filter(entry -> entry.clientId() == null)
                .filter(entry -> "ORGANISATION_ADMIN".equals(entry.role()))
                .filter(entry -> "ACTIVE".equals(entry.status()))
                .count() <= 1;
    }

    private void audit(Organisation organisation, String action, String entityId, String details) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        String actor = (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user)
                ? user.phoneNumber()
                : "SYSTEM";
        platformAuditRepository.record(actor, "PLATFORM_ADMIN", action, "User", entityId,
                organisation.organisationCode() + " · " + details);
    }

    private OrganisationUserDetailDto toUserDetail(
            Organisation organisation, UserDirectoryEntry entry, String email, String clientId, String clientName,
            String clientCode) {
        return new OrganisationUserDetailDto(
                entry.userId().toString(),
                entry.displayName(),
                entry.phoneNumber(),
                email != null ? email : entry.email(),
                entry.role(),
                entry.status(),
                entry.createdAt(),
                organisation.id().toString(),
                organisation.organisationName(),
                clientId,
                clientName,
                clientCode);
    }

    private OrganisationClientDto toClient(Client client, List<UserDirectoryEntry> directory) {
        OrganisationUserDto admin = directory.stream()
                .filter(entry -> client.getId().equals(entry.clientId()) && "CLIENT_ADMIN".equals(entry.role()))
                .min(Comparator.comparing(UserDirectoryEntry::createdAt))
                .map(this::toUser)
                .orElse(null);
        return new OrganisationClientDto(
                client.getId().toString(),
                client.getClientName(),
                client.getClientCode(),
                client.getClientType().name(),
                client.getStatus().name(),
                client.getContactPhone(),
                client.getCreatedAt(),
                admin);
    }

    private OrganisationUserDto toUser(UserDirectoryEntry entry) {
        return new OrganisationUserDto(
                entry.userId().toString(),
                entry.displayName(),
                entry.phoneNumber(),
                entry.email(),
                entry.role(),
                entry.status(),
                entry.createdAt());
    }
}
