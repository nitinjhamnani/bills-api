package com.b2bfintech.app.controller;

import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.common.security.ClientScopeGuard;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Read-only reports for a client's own bills and wallet activity. */
@RestController
@RequestMapping("/api/v1/clients/{clientId}/reports")
@PreAuthorize("hasRole('CLIENT_ADMIN')")
public class ClientReportsController {

    private final ReportQueryService reports;

    public ClientReportsController(ReportQueryService reports) {
        this.reports = reports;
    }

    /** Every bill payment cycle created in the window, any status - the client's bill statement. */
    @GetMapping("/bill-statement")
    public List<BillDtos.BillPaymentDto> billStatement(
            @PathVariable UUID clientId, @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        ReportDtos.DateRange range = ReportDtos.parseRange(from, to, 30);
        return reports.billPaymentsInRange(clientId, range.from(), range.to());
    }

    /** Bills fetched but not yet paid by the client - no date range, this is a point-in-time list. */
    @GetMapping("/outstanding-bills")
    public List<BillDtos.BillPaymentDto> outstandingBills(
            @PathVariable UUID clientId, @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        return reports.outstandingBills(clientId);
    }

    /** Wallet ledger for the window, with opening/closing balance and credit/debit totals. */
    @GetMapping("/wallet-statement")
    public ReportDtos.WalletStatementDto walletStatement(
            @PathVariable UUID clientId, @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        ReportDtos.DateRange range = ReportDtos.parseRange(from, to, 30);
        return reports.walletStatement(clientId, range.from(), range.to());
    }

    /** Cycles the client actually paid in the window (by clientPaidAt). */
    @GetMapping("/payment-history")
    public List<BillDtos.BillPaymentDto> paymentHistory(
            @PathVariable UUID clientId, @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        ReportDtos.DateRange range = ReportDtos.parseRange(from, to, 30);
        return reports.paymentHistory(clientId, range.from(), range.to());
    }

    /** Paid amount grouped by bill service in the window. */
    @GetMapping("/spend-by-service")
    public List<ReportDtos.SpendByServiceRowDto> spendByService(
            @PathVariable UUID clientId, @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        ReportDtos.DateRange range = ReportDtos.parseRange(from, to, 30);
        return reports.spendByService(clientId, range.from(), range.to());
    }

    /** This client's top-up requests in the window. */
    @GetMapping("/top-ups")
    public List<ReportDtos.FundingRowDto> topUps(
            @PathVariable UUID clientId, @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        ReportDtos.DateRange range = ReportDtos.parseRange(from, to, 30);
        return reports.clientTopUps(clientId, range.from(), range.to());
    }
}
