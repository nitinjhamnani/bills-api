package com.b2bfintech.app.controller;

import com.b2bfintech.app.service.TenantUserProvisioningService;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.organisation.entity.AppUser;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.service.ClientService;
import com.b2bfintech.organisation.service.TenantUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Organisation admin manages organisation-side users (Maker/Checker/Viewer/other Organisation Admins): creation
 * with an optional email + initial password (implementation plan &sect;8.1 extension), full
 * profile/permission edits, password reset, and suspend/reactivate.
 */
@RestController
@RequestMapping("/api/v1/paytrix/users")
@PreAuthorize("hasRole('ORGANISATION_ADMIN')")
public class OrganisationUserController {

    private static final Set<String> ASSIGNABLE_ROLES =
            Set.of("ORGANISATION_ADMIN", "MAKER", "CHECKER", "VIEWER", "SALES");

    private final TenantUserService tenantUserService;
    private final TenantUserProvisioningService provisioningService;
    private final ClientService clientService;

    public OrganisationUserController(
            TenantUserService tenantUserService,
            TenantUserProvisioningService provisioningService,
            ClientService clientService) {
        this.tenantUserService = tenantUserService;
        this.provisioningService = provisioningService;
        this.clientService = clientService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto create(@Valid @RequestBody CreateUserRequest request) {
        validateRole(request.role());
        AppUser user = provisioningService.createOrganisationUser(
                request.phoneNumber(), request.fullName(), request.role(), request.email(), request.password());
        return UserDto.from(user);
    }

    @GetMapping
    public List<UserDto> list() {
        return tenantUserService.listAll().stream()
                .filter(AppUser::isPaytrix)
                .map(UserDto::from)
                .toList();
    }

    @GetMapping("/{id}")
    public UserDetailDto get(@PathVariable String id) {
        AppUser user = tenantUserService.getById(id);
        List<AssignedCorporate> corporates = List.of();
        if (user.hasUserType(AppUser.UserType.ORG_SALES)) {
            corporates = clientService.listForSalesPerson(id).stream()
                    .filter(client -> client.getClientType() == Client.ClientType.CORPORATE)
                    .map(AssignedCorporate::from)
                    .toList();
        }
        return UserDetailDto.from(user, corporates);
    }

    /** Full profile edit - name, email, and role/permissions. Phone number (the login identifier) is immutable. */
    @PutMapping("/{id}")
    public UserDto update(@PathVariable String id, @Valid @RequestBody UpdateUserRequest request) {
        validateRole(request.role());
        AppUser existing = tenantUserService.getById(id);
        if (existing.hasUserType(AppUser.UserType.ORG_SALES)
                && !"SALES".equals(request.role())
                && clientService.hasSalesAssignments(id)) {
            throw new BadRequestException("This person is still assigned to corporates. Clear those assignments first.");
        }
        AppUser user = provisioningService.updateUserProfile(id, request.fullName(), request.email(), request.role());
        return UserDto.from(user);
    }

    @PatchMapping("/{id}/password")
    public void setPassword(@PathVariable String id, @Valid @RequestBody SetPasswordRequest request) {
        provisioningService.setUserPassword(id, request.password());
    }

    @PatchMapping("/{id}/suspend")
    public void suspend(@PathVariable String id) {
        provisioningService.suspendUser(id);
    }

    @PatchMapping("/{id}/reactivate")
    public void reactivate(@PathVariable String id) {
        provisioningService.reactivateUser(id);
    }

    private void validateRole(String role) {
        if (!ASSIGNABLE_ROLES.contains(role)) {
            throw new BadRequestException("role must be one of " + ASSIGNABLE_ROLES);
        }
    }

    public record CreateUserRequest(
            @NotBlank @Pattern(regexp = "\\d{10}") String phoneNumber,
            @NotBlank String fullName,
            @Email String email,
            @NotBlank @Size(min = 6, max = 72) String password,
            @NotBlank String role) {
    }

    public record UpdateUserRequest(@NotBlank String fullName, @Email String email, @NotBlank String role) {
    }

    public record SetPasswordRequest(@NotBlank @Size(min = 6, max = 72) String password) {
    }

    public record UserDto(String id, String phoneNumber, String userCode, String fullName, String email, String role, String status) {
        static UserDto from(AppUser u) {
            return new UserDto(
                    u.getId().toString(), u.getPhoneNumber(), u.getUserCode(), u.getFullName(), u.getEmail(),
                    u.getRole().getName(), u.getStatus().name());
        }
    }

    public record AssignedCorporate(String id, String clientName, String clientCode, String status) {
        static AssignedCorporate from(Client client) {
            return new AssignedCorporate(
                    client.getId().toString(), client.getClientName(), client.getClientCode(), client.getStatus().name());
        }
    }

    public record UserDetailDto(
            String id, String phoneNumber, String userCode, String fullName, String email, String role, String status,
            List<AssignedCorporate> corporates) {
        static UserDetailDto from(AppUser user, List<AssignedCorporate> corporates) {
            return new UserDetailDto(
                    user.getId().toString(), user.getPhoneNumber(), user.getUserCode(), user.getFullName(), user.getEmail(),
                    user.getRole().getName(), user.getStatus().name(), corporates);
        }
    }
}
