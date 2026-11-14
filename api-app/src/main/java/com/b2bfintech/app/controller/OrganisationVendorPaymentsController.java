package com.b2bfintech.app.controller;

import com.b2bfintech.app.service.CategoryLabels;
import com.b2bfintech.app.service.VendorSettlementService;
import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.billing.repository.BillRepository;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Verified vendor bills that Paytrix still has to pay. */
@RestController
@RequestMapping("/api/v1/paytrix/vendor-payments")
@PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','CHECKER','MAKER','VIEWER')")
public class OrganisationVendorPaymentsController {

    private final VendorSettlementService settlementService;
    private final BillRepository billRepository;
    private final CategoryLabels categoryLabels;

    public OrganisationVendorPaymentsController(
            VendorSettlementService settlementService, BillRepository billRepository, CategoryLabels categoryLabels) {
        this.settlementService = settlementService;
        this.billRepository = billRepository;
        this.categoryLabels = categoryLabels;
    }

    @GetMapping
    public List<BillDtos.BillPaymentDto> list(@RequestParam(required = false) String vendorClientId) {
        return settlementService.awaitingReimbursement().stream()
                .filter(payment -> vendorClientId == null || vendorClientId.isBlank()
                        || vendorClientId.equals(payment.getAssignedVendorClientId()))
                .map(this::toDto)
                .toList();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','CHECKER')")
    public List<VendorSettlementService.PayoutResult> record(
            @Valid @RequestBody RecordPaymentRequest request, @AuthenticationPrincipal AuthenticatedUser principal) {
        return settlementService.reimburseSelected(
                request.billPaymentIds(), request.reference(), request.mode(), principal);
    }

    private BillDtos.BillPaymentDto toDto(BillPayment payment) {
        Bill bill = billRepository.findById(payment.getBillId())
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found"));
        String vendorName = payment.isWithVendor() ? payment.getBillAssignedToName() : null;
        return BillDtos.BillPaymentDto.from(
                payment, bill, categoryLabels.lookup(), false, false, id -> vendorName);
    }

    public record RecordPaymentRequest(
            @NotEmpty List<UUID> billPaymentIds,
            @NotBlank @Size(max = 120) String reference,
            String mode) {
    }
}
