package com.b2bfintech.app.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Read-only, organisation-wide reports across every client. */
@RestController
@RequestMapping("/api/v1/organisation/reports")
@PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER','VIEWER')")
public class OrganisationReportsController {

    private final ReportQueryService reports;

    public OrganisationReportsController(ReportQueryService reports) {
        this.reports = reports;
    }

    /** One row per client: onboarding info, current wallet, lifetime settled, outstanding, in-flight. */
    @GetMapping("/client-portfolio")
    public List<ReportDtos.ClientPortfolioRowDto> clientPortfolio() {
        return reports.clientPortfolio();
    }

    /** Bill payment cycles in the window. Defaults to settlement statuses (excludes unpaid NA).
     * {@code dateField} is createdAt (default), clientPaidAt, or orgPaidAt. */
    @GetMapping("/settlement-pipeline")
    public List<BillDtos.BillPaymentDto> settlementPipeline(
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) String dateField,
            @RequestParam(required = false) String orgStatus) {
        ReportDtos.DateRange range = ReportDtos.parseRange(from, to, 30);
        return reports.settlementPipeline(range.from(), range.to(), clientId, dateField, orgStatus);
    }

    /** Cycles stuck in PENDING or IN_PROCESS, oldest first by days stuck. */
    @GetMapping("/aging")
    public List<ReportDtos.AgingRowDto> aging() {
        return reports.agingQueue();
    }

    /** Period GMV by client: wallet-paid vs org-settled in the same IST window. */
    @GetMapping("/gmv-by-client")
    public List<ReportDtos.ClientGmvRowDto> gmvByClient(
            @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        ReportDtos.DateRange range = ReportDtos.parseRange(from, to, 30);
        return reports.gmvByClient(range.from(), range.to());
    }

    /** Every FEE ledger entry (convenience-fee revenue) in the window. */
    @GetMapping("/revenue")
    public List<ReportDtos.RevenueRowDto> revenue(
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(required = false) UUID clientId) {
        ReportDtos.DateRange range = ReportDtos.parseRange(from, to, 30);
        return reports.revenue(range.from(), range.to(), clientId);
    }

    /** FEE revenue rolled up by client for the window. */
    @GetMapping("/revenue-by-client")
    public List<ReportDtos.RevenueByClientRowDto> revenueByClient(
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(required = false) UUID clientId) {
        ReportDtos.DateRange range = ReportDtos.parseRange(from, to, 30);
        return reports.revenueByClient(range.from(), range.to(), clientId);
    }

    /** Every wallet top-up request created in the window. */
    @GetMapping("/funding")
    public List<ReportDtos.FundingRowDto> funding(
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) String status) {
        ReportDtos.DateRange range = ReportDtos.parseRange(from, to, 30);
        return reports.funding(range.from(), range.to(), clientId, status);
    }

    /** Organisation manual wallet credits (ADJUSTMENT) in the window. */
    @GetMapping("/funding-adjustments")
    public List<ReportDtos.AdjustmentRowDto> fundingAdjustments(
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(required = false) UUID clientId) {
        ReportDtos.DateRange range = ReportDtos.parseRange(from, to, 30);
        return reports.fundingAdjustments(range.from(), range.to(), clientId);
    }
}
