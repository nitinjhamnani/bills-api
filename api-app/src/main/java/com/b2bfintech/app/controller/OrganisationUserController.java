package com.b2bfintech.app.controller;

import com.b2bfintech.app.service.TenantUserProvisioningService;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.organisation.entity.AppUser;
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
@RequestMapping("/api/v1/organisation/users")
@PreAuthorize("hasRole('ORGANISATION_ADMIN')")
public class OrganisationUserController {

    private static final Set<String> ASSIGNABLE_ROLES = Set.of("ORGANISATION_ADMIN", "MAKER", "CHECKER", "VIEWER");

    private final TenantUserService tenantUserService;
    private final TenantUserProvisioningService provisioningService;

    public OrganisationUserController(TenantUserService tenantUserService, TenantUserProvisioningService provisioningService) {
        this.tenantUserService = tenantUserService;
        this.provisioningService = provisioningService;
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
                .filter(u -> u.getClient() == null) // organisation-side users only; clients have their own listing
                .map(UserDto::from)
                .toList();
    }

    @GetMapping("/{id}")
    public UserDto get(@PathVariable UUID id) {
        return UserDto.from(tenantUserService.getById(id));
    }

    /** Full profile edit - name, email, and role/permissions. Phone number (the login identifier) is immutable. */
    @PutMapping("/{id}")
    public UserDto update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        validateRole(request.role());
        AppUser user = provisioningService.updateUserProfile(id, request.fullName(), request.email(), request.role());
        return UserDto.from(user);
    }

    @PatchMapping("/{id}/password")
    public void setPassword(@PathVariable UUID id, @Valid @RequestBody SetPasswordRequest request) {
        provisioningService.setUserPassword(id, request.password());
    }

    @PatchMapping("/{id}/suspend")
    public void suspend(@PathVariable UUID id) {
        provisioningService.suspendUser(id);
    }

    @PatchMapping("/{id}/reactivate")
    public void reactivate(@PathVariable UUID id) {
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

    public record UserDto(String id, String phoneNumber, String fullName, String email, String role, String status) {
        static UserDto from(AppUser u) {
            return new UserDto(
                    u.getId().toString(), u.getPhoneNumber(), u.getFullName(), u.getEmail(), u.getRole().getName(),
                    u.getStatus().name());
        }
    }
}
