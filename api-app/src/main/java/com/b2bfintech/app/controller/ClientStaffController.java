package com.b2bfintech.app.controller;

import com.b2bfintech.app.controller.ClientController.AddClientUserRequest;
import com.b2bfintech.app.controller.ClientController.ClientUserDto;
import com.b2bfintech.app.service.TenantUserProvisioningService;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.common.security.ClientScopeGuard;
import com.b2bfintech.organisation.entity.AppUser;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.service.ClientService;
import com.b2bfintech.organisation.service.TenantUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Client Admin manages staff for their own client. */
@RestController
@RequestMapping("/api/v1/clients/{clientId}/users")
@PreAuthorize("hasRole('CLIENT_ADMIN')")
public class ClientStaffController {

    private static final Set<String> ASSIGNABLE_ROLES = Set.of("CLIENT_ADMIN", "CLIENT_OPERATOR");

    private final ClientService clientService;
    private final TenantUserService tenantUserService;
    private final TenantUserProvisioningService provisioningService;

    public ClientStaffController(
            ClientService clientService,
            TenantUserService tenantUserService,
            TenantUserProvisioningService provisioningService) {
        this.clientService = clientService;
        this.tenantUserService = tenantUserService;
        this.provisioningService = provisioningService;
    }

    @GetMapping
    public List<ClientUserDto> list(
            @PathVariable UUID clientId, @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        return tenantUserService.listForClient(clientId).stream().map(ClientUserDto::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClientUserDto add(
            @PathVariable UUID clientId,
            @Valid @RequestBody AddClientUserRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        if (!ASSIGNABLE_ROLES.contains(request.role())) {
            throw new BadRequestException("role must be one of " + ASSIGNABLE_ROLES);
        }
        Client client = clientService.getById(clientId);
        AppUser user = provisioningService.createClientUser(
                request.phoneNumber(), request.fullName(), request.role(), client);
        return ClientUserDto.from(user);
    }

    @PatchMapping("/{userId}/suspend")
    public void suspend(
            @PathVariable UUID clientId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        AppUser target = requireClientUser(clientId, userId);
        if (isLastActiveAdmin(clientId, target)) {
            throw new BadRequestException("Cannot suspend the last active Client Admin");
        }
        provisioningService.suspendUser(userId);
    }

    @PatchMapping("/{userId}/reactivate")
    public void reactivate(
            @PathVariable UUID clientId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        requireClientUser(clientId, userId);
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
}
