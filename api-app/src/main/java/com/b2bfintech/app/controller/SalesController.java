package com.b2bfintech.app.controller;

import com.b2bfintech.app.service.SalesPortfolioService;
import com.b2bfintech.app.service.SalesPortfolioService.CorporateSummary;
import com.b2bfintech.app.service.SalesPortfolioService.SalesBill;
import com.b2bfintech.app.service.SalesPortfolioService.SalesDashboard;
import com.b2bfintech.app.service.SalesPortfolioService.SalesPaidReport;
import com.b2bfintech.common.security.AuthenticatedUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** A sales person reads only the corporates assigned to them. */
@RestController
@RequestMapping("/api/v1/paytrix/sales")
@PreAuthorize("hasRole('SALES')")
public class SalesController {

    private final SalesPortfolioService salesPortfolioService;

    public SalesController(SalesPortfolioService salesPortfolioService) {
        this.salesPortfolioService = salesPortfolioService;
    }

    @GetMapping("/dashboard")
    public SalesDashboard dashboard(@AuthenticationPrincipal AuthenticatedUser principal) {
        return salesPortfolioService.dashboard(userId(principal));
    }

    @GetMapping("/paid-bills")
    public SalesPaidReport paidBills(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) List<String> clientId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        ReportDtos.DateRange range = ReportDtos.parseRange(from, to, 30);
        return salesPortfolioService.paidInRange(userId(principal), range.from(), range.to(), clientId);
    }

    @GetMapping("/corporates/{clientId}")
    public CorporateSummary corporate(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String clientId) {
        return salesPortfolioService.corporate(userId(principal), clientId);
    }

    @GetMapping("/corporates/{clientId}/bills")
    public List<SalesBill> bills(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String clientId) {
        return salesPortfolioService.bills(userId(principal), clientId);
    }

    private static String userId(AuthenticatedUser principal) {
        return principal.userId();
    }
}
