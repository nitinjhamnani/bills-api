package com.b2bfintech.app.controller;

import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.service.ClientService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Reports for a vendor admin about bills they paid and commission they paid to Paytrix. */
@RestController
@RequestMapping("/api/v1/client/reports")
@PreAuthorize("hasRole('CLIENT_ADMIN')")
public class VendorReportsController {

    private final ReportQueryService reports;
    private final ClientService clientService;

    public VendorReportsController(ReportQueryService reports, ClientService clientService) {
        this.reports = reports;
        this.clientService = clientService;
    }

    /** Bills this vendor paid, grouped by category, with the amount. */
    @GetMapping("/bills-by-category")
    public List<ReportDtos.VendorCategoryPaidRowDto> billsByCategory(
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        ReportDtos.DateRange range = ReportDtos.parseRange(from, to, 30);
        return reports.vendorBillsByCategory(vendorId(principal), range.from(), range.to());
    }

    /** Bills this vendor paid that Paytrix has not paid back. */
    @GetMapping("/awaiting-platform")
    public List<ReportDtos.VendorAwaitingRowDto> awaitingPlatform(
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        ReportDtos.DateRange range = ReportDtos.parseRange(from, to, 30);
        return reports.vendorAwaitingPlatform(vendorId(principal), range.from(), range.to());
    }

    /** Commission this vendor paid to Paytrix. */
    @GetMapping("/commission-paid")
    public List<ReportDtos.VendorCommissionPaidRowDto> commissionPaid(
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        ReportDtos.DateRange range = ReportDtos.parseRange(from, to, 30);
        return reports.vendorCommissionPaid(vendorId(principal), range.from(), range.to());
    }

    private String vendorId(AuthenticatedUser principal) {
        if (principal == null || principal.clientId() == null || principal.clientId().isBlank()) {
            throw new BadRequestException("This report is only available to a vendor");
        }
        Client client = clientService.getById(principal.clientId());
        if (client.getClientType() != Client.ClientType.VENDOR) {
            throw new BadRequestException("Only a vendor can open this report");
        }
        return client.getId();
    }
}
