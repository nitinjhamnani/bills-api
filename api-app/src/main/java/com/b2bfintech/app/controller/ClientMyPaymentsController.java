package com.b2bfintech.app.controller;

import com.b2bfintech.app.service.BranchService;
import com.b2bfintech.app.service.ClientPaymentService;
import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.billing.repository.BillPaymentRepository;
import com.b2bfintech.billing.repository.BillRepository;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.common.security.ClientScopeGuard;
import com.b2bfintech.app.service.CategoryLabels;
import com.b2bfintech.organisation.entity.Branch;
import com.b2bfintech.organisation.repository.BranchRepository;
import com.b2bfintech.wallet.repository.LedgerEntryRepository;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The client's My Payments screen: bills awaiting the client's payment action (single or bulk). */
@RestController
@RequestMapping("/api/v1/clients/{clientId}/my-payments")
@PreAuthorize("hasAnyRole('CLIENT_ADMIN','CLIENT_OPERATOR','BRANCH_USER')")
public class ClientMyPaymentsController {

    private final ClientPaymentService clientPaymentService;
    private final BillPaymentRepository billPaymentRepository;
    private final BillRepository billRepository;
    private final CategoryLabels categoryLabels;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final BranchRepository branchRepository;
    private final BranchService branchService;

    public ClientMyPaymentsController(
            ClientPaymentService clientPaymentService, BillPaymentRepository billPaymentRepository, BillRepository billRepository,
            CategoryLabels categoryLabels, LedgerEntryRepository ledgerEntryRepository, BranchRepository branchRepository,
            BranchService branchService) {
        this.clientPaymentService = clientPaymentService;
        this.billPaymentRepository = billPaymentRepository;
        this.billRepository = billRepository;
        this.categoryLabels = categoryLabels;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.branchRepository = branchRepository;
        this.branchService = branchService;
    }

    @GetMapping
    public List<BillDtos.BillPaymentDto> list(@PathVariable UUID clientId, @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        Function<String, String> names = categoryLabels.lookup();
        return billPaymentRepository.findByClientIdAndPaymentStatusOrderByCreatedAtDesc(clientId, Bill.PaymentStatus.PENDING)
                .stream().filter(payment -> visibleTo(principal, payment)).map(p -> toDto(p, names)).toList();
    }

    @PostMapping("/pay")
    @PreAuthorize("hasAnyRole('CLIENT_ADMIN','CLIENT_OPERATOR')")
    public List<BillDtos.BillPaymentDto> pay(@PathVariable UUID clientId, @RequestBody PayRequest request,
                                              @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        if ("BRANCH_USER".equals(principal.role())) {
            throw new BadRequestException("A branch cannot pay bills");
        }
        Function<String, String> names = categoryLabels.lookup();
        List<BillPayment> paid = clientPaymentService.pay(clientId, request.billPaymentIds(), principal.phoneNumber());
        return paid.stream().map(p -> toDto(p, names)).toList();
    }

    @GetMapping("/{billPaymentId}/transactions")
    public List<BillDtos.LedgerEntryDto> transactions(@PathVariable UUID clientId, @PathVariable UUID billPaymentId,
                                                        @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        BillPayment payment = getOwnedPayment(clientId, billPaymentId);
        if (!visibleTo(principal, payment)) {
            throw new ResourceNotFoundException("Bill payment not found");
        }
        return ledgerEntryRepository.findByReferenceIdOrderByCreatedAtDesc(billPaymentId.toString()).stream()
                .map(BillDtos.LedgerEntryDto::from).toList();
    }

    private BillPayment getOwnedPayment(UUID clientId, UUID billPaymentId) {
        BillPayment payment = billPaymentRepository.findById(billPaymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill payment not found"));
        if (!payment.getClientId().equals(clientId)) {
            throw new ResourceNotFoundException("Bill payment not found");
        }
        return payment;
    }

    private boolean visibleTo(AuthenticatedUser principal, BillPayment payment) {
        if (!"BRANCH_USER".equals(principal.role())) {
            return true;
        }
        Bill bill = billRepository.findById(payment.getBillId()).orElse(null);
        return bill != null && branchService.branchUserCanSee(principal, bill.getBranchId());
    }

    private BillDtos.BillPaymentDto toDto(BillPayment payment, Function<String, String> names) {
        Bill bill = billRepository.findById(payment.getBillId()).orElseThrow(() -> new ResourceNotFoundException("Bill not found"));
        Function<UUID, String> branches = branchRepository.findAll().stream()
                .collect(Collectors.toMap(Branch::getId, Branch::getName, (left, right) -> left))::get;
        return BillDtos.BillPaymentDto.from(payment, bill, names, false, false, null, null, null, branches);
    }

    public record PayRequest(@NotEmpty List<UUID> billPaymentIds) {
    }
}
