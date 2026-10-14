package com.b2bfintech.app.controller;

import com.b2bfintech.app.service.TenantUserProvisioningService;
import com.b2bfintech.common.audit.AuditService;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.entity.AppUser;
import com.b2bfintech.organisation.service.ClientService;
import com.b2bfintech.organisation.service.TenantUserService;
import com.b2bfintech.wallet.entity.LedgerEntry;
import com.b2bfintech.wallet.entity.WalletAccount;
import com.b2bfintech.wallet.service.WalletService;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Organisation admin manages the lifecycle of its clients (implementation plan &sect;4.2/&sect;4.3). */
@RestController
@RequestMapping("/api/v1/organisation/clients")
public class ClientController {

    private static final Set<String> ASSIGNABLE_CLIENT_ROLES = Set.of("CLIENT_ADMIN", "CLIENT_OPERATOR");

    private final ClientService clientService;
    private final TenantUserProvisioningService provisioningService;
    private final TenantUserService tenantUserService;
    private final WalletService walletService;
    private final AuditService auditService;

    public ClientController(
            ClientService clientService,
            TenantUserProvisioningService provisioningService,
            TenantUserService tenantUserService,
            WalletService walletService,
            AuditService auditService) {
        this.clientService = clientService;
        this.provisioningService = provisioningService;
        this.tenantUserService = tenantUserService;
        this.walletService = walletService;
        this.auditService = auditService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ClientDto create(@Valid @RequestBody CreateClientRequest request) {
        // Transactional so a failure provisioning the admin user (duplicate phone/email, etc.)
        // rolls back the client row too, instead of leaving a phantom client with no admin.
        Client client = clientService.create(request.clientName(), request.phoneNumber(), request.clientType());
        provisioningService.createClientUser(
                request.phoneNumber(), request.clientName(), "CLIENT_ADMIN", client, request.email(), request.password());
        creditOpeningBalance(client, request.openingBalance());
        return ClientDto.from(client);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER','VIEWER')")
    public List<ClientDto> list() {
        return clientService.listAll().stream().map(ClientDto::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER','VIEWER')")
    public ClientDto get(@PathVariable UUID id) {
        return ClientDto.from(clientService.getById(id));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public ClientDto setStatus(@PathVariable UUID id, @RequestBody StatusRequest request) {
        Client client = clientService.setStatus(id, Client.ClientStatus.valueOf(request.status()));
        return ClientDto.from(client);
    }

    /** Adds another staff member (admin or operator) to an already-onboarded client. */
    @PostMapping("/{id}/users")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ClientUserDto addUser(@PathVariable UUID id, @Valid @RequestBody AddClientUserRequest request) {
        if (!ASSIGNABLE_CLIENT_ROLES.contains(request.role())) {
            throw new BadRequestException("role must be one of " + ASSIGNABLE_CLIENT_ROLES);
        }
        Client client = clientService.getById(id);
        AppUser user = provisioningService.createClientUser(request.phoneNumber(), request.fullName(), request.role(), client);
        return ClientUserDto.from(user);
    }

    @GetMapping("/{id}/users")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public List<ClientUserDto> listUsers(@PathVariable UUID id) {
        return tenantUserService.listForClient(id).stream().map(ClientUserDto::from).toList();
    }

    @PatchMapping("/{id}/users/{userId}/suspend")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public void suspendUser(@PathVariable UUID id, @PathVariable UUID userId) {
        AppUser target = requireClientUser(id, userId);
        if (isLastActiveAdmin(id, target)) {
            throw new BadRequestException("Cannot suspend the last active Client Admin");
        }
        provisioningService.suspendUser(userId);
    }

    @PatchMapping("/{id}/users/{userId}/reactivate")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public void reactivateUser(@PathVariable UUID id, @PathVariable UUID userId) {
        requireClientUser(id, userId);
        provisioningService.reactivateUser(userId);
    }

    private AppUser requireClientUser(UUID clientId, UUID userId) {
        AppUser user = tenantUserService.getById(userId);
        if (user.getClient() == null || !clientId.equals(user.getClient().getId())) {
            throw new ResourceNotFoundException("User not found");
        }
        return user;
    }

    private boolean isLastActiveAdmin(UUID clientId, AppUser target) {
        if (!"CLIENT_ADMIN".equals(target.getRole().getName())) return false;
        return tenantUserService.listForClient(clientId).stream()
                .filter(user -> "CLIENT_ADMIN".equals(user.getRole().getName()))
                .filter(user -> user.getStatus() == AppUser.UserStatus.ACTIVE)
                .count() <= 1;
    }

    public record AddClientUserRequest(
            @NotBlank @Pattern(regexp = "\\d{10}") String phoneNumber,
            @NotBlank String fullName,
            @NotBlank String role) {
    }

    public record ClientUserDto(String id, String phoneNumber, String fullName, String role, String status) {
        static ClientUserDto from(AppUser u) {
            return new ClientUserDto(
                    u.getId().toString(), u.getPhoneNumber(), u.getFullName(), u.getRole().getName(), u.getStatus().name());
        }
    }

    /**
     * The client's phone number doubles as both its contact number and its first admin's login
     * phone. The password registers against the phone number (and email, if given).
     */
    public record CreateClientRequest(
            @NotBlank String clientName,
            @NotBlank @Pattern(regexp = "\\d{10}") String phoneNumber,
            @Email String email,
            @NotBlank @Size(min = 6, max = 72) String password,
            @NotNull Client.ClientType clientType,
            @DecimalMin(value = "0.00", inclusive = true) @Digits(integer = 12, fraction = 2) BigDecimal openingBalance) {
    }

    private void creditOpeningBalance(Client client, BigDecimal openingBalance) {
        if (openingBalance == null || openingBalance.signum() <= 0) {
            return;
        }
        walletService.credit(
                client.getId(), WalletAccount.WalletType.WALLET, openingBalance,
                LedgerEntry.ReferenceType.ADJUSTMENT, client.getId().toString(),
                "onboard-credit-" + client.getId(),
                "Opening balance on client onboard");
        auditService.record("WALLET_OPENING_CREDIT", "Client", client.getId().toString(),
                "credited opening balance " + openingBalance);
    }

    public record StatusRequest(@NotBlank String status) {
    }

    public record ClientDto(String id, String clientName, String clientCode, String contactPhone, String clientType, String status, Instant createdAt) {
        static ClientDto from(Client a) {
            return new ClientDto(a.getId().toString(), a.getClientName(), a.getClientCode(), a.getContactPhone(),
                    a.getClientType().name(), a.getStatus().name(), a.getCreatedAt());
        }
    }
}
