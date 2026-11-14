package com.b2bfintech.app.controller;

import com.b2bfintech.app.service.BranchService;
import com.b2bfintech.app.service.CategoryLabels;
import com.b2bfintech.app.service.ClientPaymentService;
import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillFetch;
import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.billing.repository.BillFetchRepository;
import com.b2bfintech.billing.repository.BillPaymentRepository;
import com.b2bfintech.billing.repository.BillRepository;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.common.security.ClientScopeGuard;
import com.b2bfintech.organisation.entity.Branch;
import com.b2bfintech.organisation.repository.BranchRepository;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The corporate's My Dues (unpaid fetches) and My Payments (bill_payment rows). */
@RestController
@RequestMapping("/api/v1/clients/{clientId}")
@PreAuthorize("hasAnyRole('CLIENT_ADMIN','CLIENT_OPERATOR','BRANCH_USER')")
public class ClientMyPaymentsController {

    private final ClientPaymentService clientPaymentService;
    private final BillPaymentRepository billPaymentRepository;
    private final BillFetchRepository billFetchRepository;
    private final BillRepository billRepository;
    private final CategoryLabels categoryLabels;
    private final BranchRepository branchRepository;
    private final BranchService branchService;

    public ClientMyPaymentsController(
            ClientPaymentService clientPaymentService, BillPaymentRepository billPaymentRepository,
            BillFetchRepository billFetchRepository, BillRepository billRepository,
            CategoryLabels categoryLabels, BranchRepository branchRepository,
            BranchService branchService) {
        this.clientPaymentService = clientPaymentService;
        this.billPaymentRepository = billPaymentRepository;
        this.billFetchRepository = billFetchRepository;
        this.billRepository = billRepository;
        this.categoryLabels = categoryLabels;
        this.branchRepository = branchRepository;
        this.branchService = branchService;
    }

    @GetMapping("/my-dues")
    public List<BillDtos.BillPaymentDto> dues(@PathVariable String clientId, @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        Function<String, String> names = categoryLabels.lookup();
        Function<String, String> branches = branchNames();
        List<BillFetch> dues = billFetchRepository.findDues(clientId);
        Map<String, Bill> bills = billRepository.findAllById(dues.stream().map(BillFetch::getBillId).toList()).stream()
                .collect(Collectors.toMap(Bill::getId, Function.identity()));
        return dues.stream()
                .filter(fetch -> bills.containsKey(fetch.getBillId()))
                .filter(fetch -> visibleTo(principal, bills.get(fetch.getBillId())))
                .map(fetch -> BillDtos.BillPaymentDto.fromDue(fetch, bills.get(fetch.getBillId()), names, branches))
                .toList();
    }

    @PostMapping("/my-dues/pay")
    @PreAuthorize("hasAnyRole('CLIENT_ADMIN','CLIENT_OPERATOR')")
    public List<BillDtos.CorpPaymentDto> pay(@PathVariable String clientId, @RequestBody PayRequest request,
                                             @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        if ("BRANCH_USER".equals(principal.role())) {
            throw new BadRequestException("A branch cannot pay bills");
        }
        Function<String, String> branches = branchNames();
        return clientPaymentService.payDues(clientId, request.billFetchIds(), principal.userId()).stream()
                .map(payment -> toDto(payment, branches))
                .toList();
    }

    @GetMapping("/my-payments")
    public List<BillDtos.CorpPaymentDto> payments(@PathVariable String clientId, @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        Function<String, String> branches = branchNames();
        return billPaymentRepository.findByClientIdOrderByCreatedAtDesc(clientId).stream()
                .filter(payment -> visibleTo(principal, billOf(payment)))
                .map(payment -> toDto(payment, branches))
                .toList();
    }

    private Bill billOf(BillPayment payment) {
        return billRepository.findById(payment.getBillId()).orElseThrow(() -> new ResourceNotFoundException("Bill not found"));
    }

    private boolean visibleTo(AuthenticatedUser principal, Bill bill) {
        if (!"BRANCH_USER".equals(principal.role())) {
            return true;
        }
        return bill != null && branchService.branchUserCanSee(principal, bill.getBranchId());
    }

    private BillDtos.CorpPaymentDto toDto(BillPayment payment, Function<String, String> branches) {
        return BillDtos.CorpPaymentDto.from(payment, billOf(payment), branches);
    }

    private Function<String, String> branchNames() {
        Map<String, String> names = branchRepository.findAll().stream()
                .collect(Collectors.toMap(Branch::getId, Branch::getName, (left, right) -> left));
        return names::get;
    }

    public record PayRequest(@NotEmpty List<UUID> billFetchIds) {
    }
}
