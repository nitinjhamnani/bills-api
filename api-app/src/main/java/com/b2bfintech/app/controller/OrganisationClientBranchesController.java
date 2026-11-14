package com.b2bfintech.app.controller;

import com.b2bfintech.app.service.BranchService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Branches of one corporate, for the client detail page. Paytrix does not create or edit them here. */
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
                .map(row -> new BranchRow(
                        row.branch().getId().toString(),
                        row.branch().getName(),
                        row.branch().getCode(),
                        row.managedBillCount(),
                        row.userCount(),
                        row.status()))
                .toList();
    }

    public record BranchRow(
            String id, String name, String code, long managedBillCount, int userCount, String status) {
    }
}
