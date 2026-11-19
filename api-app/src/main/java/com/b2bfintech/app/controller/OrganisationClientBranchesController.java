package com.b2bfintech.app.controller;

import com.b2bfintech.app.service.BranchService;
import com.b2bfintech.organisation.entity.AppUser;
import com.b2bfintech.organisation.entity.Branch;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Branches of one corporate, for the client detail page. */
@RestController
@RequestMapping("/api/v1/paytrix/clients/{clientId}/branches")
@PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER','VIEWER')")
public class OrganisationClientBranchesController {

    private final BranchService branchService;

    public OrganisationClientBranchesController(BranchService branchService) {
        this.branchService = branchService;
    }

    @GetMapping
    public List<BranchRow> list(@PathVariable String clientId) {
        return branchService.overview(clientId).stream()
                .map(OrganisationClientBranchesController::toRow)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public BranchRow create(@PathVariable String clientId, @Valid @RequestBody CreateBranchRequest request) {
        Branch branch = branchService.create(
                clientId,
                request.name(),
                request.address(),
                request.state(),
                request.location(),
                request.code(),
                null,
                null,
                null);
        return toRow(branchService.overviewOne(clientId, branch.getId()));
    }

    @PatchMapping("/{branchId}/suspend")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public BranchRow suspend(@PathVariable String clientId, @PathVariable String branchId) {
        branchService.setBranchActive(clientId, branchId, false);
        return toRow(branchService.overviewOne(clientId, branchId));
    }

    @PatchMapping("/{branchId}/reactivate")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public BranchRow reactivate(@PathVariable String clientId, @PathVariable String branchId) {
        branchService.setBranchActive(clientId, branchId, true);
        return toRow(branchService.overviewOne(clientId, branchId));
    }

    @PutMapping("/{branchId}/users")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public BranchRow updateUsers(
            @PathVariable String clientId,
            @PathVariable String branchId,
            @RequestBody UpdateBranchUsersRequest request) {
        branchService.setUsersForBranch(clientId, branchId, request.userIds());
        return toRow(branchService.overviewOne(clientId, branchId));
    }

    private static BranchRow toRow(BranchService.BranchOverview row) {
        List<BranchUserDto> users = row.users() == null
                ? List.of()
                : row.users().stream().map(BranchUserDto::from).toList();
        return new BranchRow(
                row.branch().getId().toString(),
                row.branch().getName(),
                row.branch().getCode(),
                row.branch().getAddress(),
                row.branch().getState(),
                row.branch().getLocation(),
                row.managedBillCount(),
                row.userCount(),
                row.status(),
                users);
    }

    public record CreateBranchRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String address,
            @Size(max = 100) String state,
            @Size(max = 150) String location,
            @Size(max = 32) String code) {
    }

    public record UpdateBranchUsersRequest(List<String> userIds) {
    }

    public record BranchUserDto(String id, String fullName, String userCode, String phoneNumber) {
        static BranchUserDto from(AppUser u) {
            return new BranchUserDto(u.getId(), u.getFullName(), u.getUserCode(), u.getPhoneNumber());
        }
    }

    public record BranchRow(
            String id,
            String name,
            String code,
            String address,
            String state,
            String location,
            long managedBillCount,
            int userCount,
            String status,
            List<BranchUserDto> users) {
    }
}
