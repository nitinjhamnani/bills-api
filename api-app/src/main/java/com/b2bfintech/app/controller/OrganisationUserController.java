package com.b2bfintech.app.controller;

import com.b2bfintech.app.service.BranchService;
import com.b2bfintech.app.service.TenantUserProvisioningService;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.organisation.entity.AppUser;
import com.b2bfintech.organisation.entity.Branch;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.entity.UserTypes;
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
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Organisation admin manages users across Platform, Corporates, and Vendors.
 */
@RestController
@RequestMapping("/api/v1/paytrix/users")
@PreAuthorize("hasRole('ORGANISATION_ADMIN')")
public class OrganisationUserController {

    private final TenantUserService tenantUserService;
    private final TenantUserProvisioningService provisioningService;
    private final ClientService clientService;
    private final BranchService branchService;

    public OrganisationUserController(
            TenantUserService tenantUserService,
            TenantUserProvisioningService provisioningService,
            ClientService clientService,
            BranchService branchService) {
        this.tenantUserService = tenantUserService;
        this.provisioningService = provisioningService;
        this.clientService = clientService;
        this.branchService = branchService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto create(@Valid @RequestBody CreateUserRequest request) {
        validatePlatformRole(request.role());
        AppUser user = provisioningService.createOrganisationUser(
                request.phoneNumber(), request.fullName(), request.role(), request.email(), request.password());
        return UserDto.from(user);
    }

    @GetMapping
    public List<UserDto> list() {
        return tenantUserService.listAll().stream()
                .filter(AppUser::isPaytrix)
                .filter(user -> !user.isSuperAdmin())
                .map(UserDto::from)
                .toList();
    }

    @GetMapping("/directory")
    public List<DirectoryUserDto> directory() {
        Map<String, Client> clientsById = clientService.listAll().stream()
                .collect(Collectors.toMap(Client::getId, Function.identity(), (a, b) -> a));
        return tenantUserService.listAll().stream()
                .filter(user -> !user.isSuperAdmin())
                .map(user -> toDirectoryDto(user, clientsById))
                .toList();
    }

    private DirectoryUserDto toDirectoryDto(AppUser user, Map<String, Client> clientsById) {
        if (user.isPaytrix()) {
            return new DirectoryUserDto(
                    user.getId(),
                    user.getUserCode(),
                    user.getFullName(),
                    user.getPhoneNumber(),
                    user.getEmail(),
                    "PLATFORM",
                    user.getRole().getName(),
                    user.getStatus().name(),
                    null,
                    null,
                    null,
                    List.of());
        }
        String orgId = user.organisationId();
        Client client = orgId == null ? null : clientsById.get(orgId);
        boolean vendor = user.hasUserType(AppUser.UserType.VENDOR_ADMIN)
                || user.hasUserType(AppUser.UserType.VENDOR_USER)
                || (client != null && client.getClientType() == Client.ClientType.VENDOR);
        String scope = vendor ? "VENDOR" : "CORPORATE";
        String role = vendor ? user.getRole().getName() : branchService.screenRole(user);
        List<ClientController.UserBranchDto> branches = List.of();
        if (!vendor && client != null) {
            List<Branch> assigned = branchService.branchesForUser(client.getId(), user.getUserId());
            branches = assigned.stream().map(ClientController.UserBranchDto::from).toList();
        }
        return new DirectoryUserDto(
                user.getId(),
                user.getUserCode(),
                user.getFullName(),
                user.getPhoneNumber(),
                user.getEmail(),
                scope,
                role,
                user.getStatus().name(),
                orgId,
                client == null ? orgId : client.getClientName(),
                client == null ? orgId : client.getClientCode(),
                branches);
    }

    @GetMapping("/{id}")
    public UserDetailDto get(@PathVariable String id) {
        AppUser user = requireManageableUser(id);
        return toUserDetailDto(user);
    }

    @PutMapping("/{id}")
    public UserDetailDto update(@PathVariable String id, @Valid @RequestBody UpdateUserRequest request) {
        AppUser existing = requireManageableUser(id);
        if (existing.isPaytrix()) {
            validatePlatformRole(request.role());
            if (existing.hasUserType(AppUser.UserType.ORG_SALES)
                    && !"SALES".equals(request.role())
                    && clientService.hasSalesAssignments(id)) {
                throw new BadRequestException("This person is still assigned to corporates. Clear those assignments first.");
            }
        } else {
            boolean vendor = existing.hasUserType(AppUser.UserType.VENDOR_ADMIN)
                    || existing.hasUserType(AppUser.UserType.VENDOR_USER);
            Set<String> allowed = vendor ? UserTypes.VENDOR_ASSIGNABLE_ROLES : UserTypes.CORPORATE_ASSIGNABLE_ROLES;
            if (!allowed.contains(request.role())) {
                throw new BadRequestException("role must be one of " + allowed);
            }
        }
        AppUser user = provisioningService.updateUserProfile(id, request.fullName(), request.email(), request.role());
        return toUserDetailDto(user);
    }

    @PutMapping("/{id}/corporates")
    public UserDetailDto assignCorporates(@PathVariable String id, @RequestBody AssignCorporatesRequest request) {
        AppUser user = requireManageableUser(id);
        if (!user.hasUserType(AppUser.UserType.ORG_SALES)) {
            throw new BadRequestException("Corporates are assigned to a sales person");
        }
        clientService.replaceSalesCorporates(id, request.clientIds() == null ? List.of() : request.clientIds());
        return toUserDetailDto(tenantUserService.getById(id));
    }

    @PatchMapping("/{id}/password")
    public void setPassword(@PathVariable String id, @Valid @RequestBody SetPasswordRequest request) {
        requireManageableUser(id);
        provisioningService.setUserPassword(id, request.password());
    }

    @PatchMapping("/{id}/suspend")
    public void suspend(@PathVariable String id) {
        requireManageableUser(id);
        provisioningService.suspendUser(id);
    }

    @PatchMapping("/{id}/reactivate")
    public void reactivate(@PathVariable String id) {
        requireManageableUser(id);
        provisioningService.reactivateUser(id);
    }

    private AppUser requireManageableUser(String id) {
        AppUser user = tenantUserService.getById(id);
        if (user.isSuperAdmin()) {
            throw new ResourceNotFoundException("User not found");
        }
        return user;
    }

    private UserDetailDto toUserDetailDto(AppUser user) {
        if (user.isPaytrix()) {
            List<AssignedCorporate> corporates = List.of();
            if (user.hasUserType(AppUser.UserType.ORG_SALES)) {
                corporates = clientService.listForSalesPerson(user.getId()).stream()
                        .filter(client -> client.getClientType() == Client.ClientType.CORPORATE)
                        .map(AssignedCorporate::from)
                        .toList();
            }
            return new UserDetailDto(
                    user.getId(),
                    user.getPhoneNumber(),
                    user.getUserCode(),
                    user.getFullName(),
                    user.getEmail(),
                    "PLATFORM",
                    user.getRole().getName(),
                    user.getStatus().name(),
                    user.getCreatedOn(),
                    null,
                    null,
                    null,
                    List.of(),
                    corporates);
        }
        String orgId = user.organisationId();
        Client client = orgId == null ? null : clientService.find(orgId).orElse(null);
        boolean vendor = user.hasUserType(AppUser.UserType.VENDOR_ADMIN)
                || user.hasUserType(AppUser.UserType.VENDOR_USER)
                || (client != null && client.getClientType() == Client.ClientType.VENDOR);
        String scope = vendor ? "VENDOR" : "CORPORATE";
        String role = vendor ? user.getRole().getName() : branchService.screenRole(user);
        List<ClientController.UserBranchDto> branches = List.of();
        if (!vendor && client != null) {
            List<Branch> assigned = branchService.branchesForUser(client.getId(), user.getUserId());
            branches = assigned.stream().map(ClientController.UserBranchDto::from).toList();
        }
        return new UserDetailDto(
                user.getId(),
                user.getPhoneNumber(),
                user.getUserCode(),
                user.getFullName(),
                user.getEmail(),
                scope,
                role,
                user.getStatus().name(),
                user.getCreatedOn(),
                orgId,
                client == null ? orgId : client.getClientName(),
                client == null ? orgId : client.getClientCode(),
                branches,
                List.of());
    }

    private void validatePlatformRole(String role) {
        if (!UserTypes.PLATFORM_ASSIGNABLE_ROLES.contains(role)) {
            throw new BadRequestException("role must be one of " + UserTypes.PLATFORM_ASSIGNABLE_ROLES);
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

    public record AssignCorporatesRequest(List<String> clientIds) {
    }

    public record UserDto(String id, String phoneNumber, String userCode, String fullName, String email, String role, String status) {
        static UserDto from(AppUser u) {
            return new UserDto(
                    u.getId(), u.getPhoneNumber(), u.getUserCode(), u.getFullName(), u.getEmail(),
                    u.getRole().getName(), u.getStatus().name());
        }
    }

    public record AssignedCorporate(String id, String clientName, String clientCode, String status) {
        static AssignedCorporate from(Client client) {
            return new AssignedCorporate(
                    client.getId(), client.getClientName(), client.getClientCode(), client.getStatus().name());
        }
    }

    public record UserDetailDto(
            String id,
            String phoneNumber,
            String userCode,
            String fullName,
            String email,
            String scope,
            String role,
            String status,
            java.time.Instant createdAt,
            String clientId,
            String clientName,
            String clientCode,
            List<ClientController.UserBranchDto> branches,
            List<AssignedCorporate> corporates) {
    }

    public record DirectoryUserDto(
            String id,
            String userCode,
            String fullName,
            String phoneNumber,
            String email,
            String scope,
            String role,
            String status,
            String clientId,
            String clientName,
            String clientCode,
            List<ClientController.UserBranchDto> branches) {
    }
}
