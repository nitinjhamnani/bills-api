package com.b2bfintech.app.controller;

import com.b2bfintech.app.service.BranchService;
import com.b2bfintech.app.service.DisputeService;
import com.b2bfintech.app.service.VendorPaymentService;
import com.b2bfintech.app.service.VendorSettlementService;
import com.b2bfintech.app.service.WalletTopupService;
import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillDispute;
import com.b2bfintech.billing.entity.BillFetch;
import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.billing.entity.VendorPayment;
import com.b2bfintech.billing.repository.BillFetchRepository;
import com.b2bfintech.billing.repository.BillPaymentRepository;
import com.b2bfintech.billing.repository.BillRepository;
import com.b2bfintech.billing.repository.VendorPaymentRepository;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.common.security.ClientScopeGuard;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.service.ClientService;
import com.b2bfintech.wallet.entity.WalletTransactionRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lightweight sidebar menu badge counts for Paytrix, Corporate, and Vendor portals so users see
 * actionable waiting items immediately upon login and after mutations.
 */
@RestController
public class NavCountsController {

    private final BillPaymentRepository billPaymentRepository;
    private final BillRepository billRepository;
    private final BillFetchRepository billFetchRepository;
    private final VendorPaymentRepository vendorPaymentRepository;
    private final WalletTopupService walletTopupService;
    private final DisputeService disputeService;
    private final VendorSettlementService vendorSettlementService;
    private final VendorPaymentService vendorPaymentService;
    private final BranchService branchService;
    private final ClientService clientService;

    public NavCountsController(
            BillPaymentRepository billPaymentRepository,
            BillRepository billRepository,
            BillFetchRepository billFetchRepository,
            VendorPaymentRepository vendorPaymentRepository,
            WalletTopupService walletTopupService,
            DisputeService disputeService,
            VendorSettlementService vendorSettlementService,
            VendorPaymentService vendorPaymentService,
            BranchService branchService,
            ClientService clientService) {
        this.billPaymentRepository = billPaymentRepository;
        this.billRepository = billRepository;
        this.billFetchRepository = billFetchRepository;
        this.vendorPaymentRepository = vendorPaymentRepository;
        this.walletTopupService = walletTopupService;
        this.disputeService = disputeService;
        this.vendorSettlementService = vendorSettlementService;
        this.vendorPaymentService = vendorPaymentService;
        this.branchService = branchService;
        this.clientService = clientService;
    }

    @GetMapping("/api/v1/paytrix/nav-counts")
    @PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER','VIEWER')")
    @Transactional(readOnly = true)
    public PaytrixNavCounts paytrixCounts() {
        long billPayments = billPaymentRepository.findAllTableRowsNewestFirst().stream()
                .filter(row -> row.orgStatus() != BillPayment.OrgStatus.PAID)
                .count();

        long walletTopups = walletTopupService.listPending().size();

        long billDues = billRepository.findAllByOrderByBillIdDesc().stream()
                .filter(NavCountsController::isDueBill)
                .count();

        long disputes = disputeService.listAll().stream()
                .filter(d -> d.getStatus() == BillDispute.Status.RAISED
                        || d.getStatus() == BillDispute.Status.UNDER_REVIEW)
                .count();

        long pendingPayables = vendorSettlementService.awaitingReimbursement().size();
        List<VendorPayment> openCommissions = vendorPaymentRepository.findByPaymentStatus(VendorPayment.PaymentStatus.PAID)
                .stream()
                .filter(vp -> !vp.isCommissionReceived() && vp.getCommissionPercent() != null)
                .toList();
        long pendingReceivables = 0L;
        if (!openCommissions.isEmpty()) {
            Set<UUID> existingPaymentIds = billPaymentRepository.findAllById(
                            openCommissions.stream().map(VendorPayment::getBillPaymentId).toList())
                    .stream()
                    .map(BillPayment::getId)
                    .collect(Collectors.toSet());
            pendingReceivables = openCommissions.stream()
                    .filter(vp -> existingPaymentIds.contains(vp.getBillPaymentId()))
                    .count();
        }
        long vendorPayments = pendingPayables + pendingReceivables;

        return new PaytrixNavCounts(billPayments, walletTopups, billDues, disputes, vendorPayments);
    }

    @GetMapping("/api/v1/clients/{clientId}/nav-counts")
    @PreAuthorize("hasAnyRole('CORP_ADMIN','CORP_OPERATOR','BRANCH_USER')")
    @Transactional(readOnly = true)
    public CorporateNavCounts corporateCounts(
            @PathVariable String clientId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCorporateAccess(principal, clientId);

        List<BillPayment> payments = billPaymentRepository.findByClientIdOrderByCreatedAtDesc(clientId);
        List<BillFetch> dues = billFetchRepository.findDues(clientId);

        Set<String> billIds = new HashSet<>();
        payments.forEach(p -> billIds.add(p.getBillId()));
        dues.forEach(d -> billIds.add(d.getBillId()));

        Map<String, Bill> bills = billIds.isEmpty()
                ? Map.of()
                : billRepository.findAllById(billIds).stream()
                        .collect(Collectors.toMap(Bill::getId, Function.identity(), (left, right) -> left));

        long billPayments = payments.stream()
                .filter(p -> bills.containsKey(p.getBillId()))
                .filter(p -> visibleTo(principal, bills.get(p.getBillId())))
                .filter(p -> p.getOrgStatus() != BillPayment.OrgStatus.PAID)
                .count();

        long billDues = dues.stream()
                .filter(f -> bills.containsKey(f.getBillId()))
                .filter(f -> visibleTo(principal, bills.get(f.getBillId())))
                .count();

        long walletTopups = "CORP_ADMIN".equals(principal.role())
                ? walletTopupService.listForClient(clientId).stream()
                        .filter(r -> r.getStatus() == WalletTransactionRequest.Status.PENDING)
                        .count()
                : 0L;

        return new CorporateNavCounts(billPayments, billDues, walletTopups);
    }

    @GetMapping("/api/v1/client/vendor-nav-counts")
    @PreAuthorize("hasAnyRole('VENDOR_ADMIN','VENDOR_OPERATOR')")
    @Transactional(readOnly = true)
    public VendorNavCounts vendorCounts(@AuthenticationPrincipal AuthenticatedUser principal) {
        if (principal == null || principal.clientId() == null || principal.clientId().isBlank()) {
            throw new BadRequestException("This action is only available to a vendor");
        }
        ClientScopeGuard.assertVendorAccess(principal, principal.clientId());
        Client vendor = clientService.getById(principal.clientId());
        if (vendor.getClientType() != Client.ClientType.VENDOR) {
            throw new BadRequestException("Only a vendor can do this");
        }

        long billPayments = vendorPaymentService.assigned(vendor.getId()).stream()
                .filter(NavCountsController::stillWithVendor)
                .count();

        long vendorTransactions = vendorSettlementService.transactions(vendor.getId()).stream()
                .filter(t -> "PENDING".equals(t.paymentStatus())
                        || (t.commissionPercent() != null && "PENDING".equals(t.commissionStatus())))
                .count();

        return new VendorNavCounts(billPayments, vendorTransactions);
    }

    private boolean visibleTo(AuthenticatedUser principal, Bill bill) {
        if (!"BRANCH_USER".equals(principal.role())) {
            return true;
        }
        return bill != null && branchService.branchUserCanSee(principal, bill.getBranchId());
    }

    private static boolean stillWithVendor(BillPayment payment) {
        BillPayment.VendorStage stage = payment.getVendorStage();
        return stage == null
                || stage == BillPayment.VendorStage.ASSIGNED
                || stage == BillPayment.VendorStage.IN_PROGRESS;
    }

    private static boolean isDueBill(Bill bill) {
        BillFetch fetch = bill.getLatestFetch();
        if (fetch == null) return false;
        if (fetch.getFetchStatus() != BillFetch.FetchStatus.SUCCESS) return false;
        if (fetch.getBillPaymentStatus() != BillFetch.PaymentStatus.PENDING) return false;
        return fetch.getBillStatus() == BillFetch.BillStatus.DUE
                || (fetch.getBillAmount() != null && fetch.getBillAmount().compareTo(BigDecimal.ZERO) > 0);
    }

    public record PaytrixNavCounts(
            long billPayments,
            long walletTopups,
            long billDues,
            long disputes,
            long vendorPayments) {
    }

    public record CorporateNavCounts(
            long billPayments,
            long billDues,
            long walletTopups) {
    }

    public record VendorNavCounts(
            long billPayments,
            long vendorTransactions) {
    }
}
