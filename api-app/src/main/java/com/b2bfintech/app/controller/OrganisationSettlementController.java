package com.b2bfintech.app.controller;

import com.b2bfintech.app.service.CategoryLabels;
import com.b2bfintech.app.service.VendorSettlementService;
import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.billing.entity.CommissionCycle;
import com.b2bfintech.billing.entity.VendorReimbursement;
import com.b2bfintech.billing.repository.BillRepository;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.organisation.service.ClientService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/paytrix/settlements")
@PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','CHECKER')")
public class OrganisationSettlementController {

    private final VendorSettlementService settlementService;
    private final BillRepository billRepository;
    private final CategoryLabels categoryLabels;
    private final ClientService clientService;

    public OrganisationSettlementController(
            VendorSettlementService settlementService, BillRepository billRepository,
            CategoryLabels categoryLabels, ClientService clientService) {
        this.settlementService = settlementService;
        this.billRepository = billRepository;
        this.categoryLabels = categoryLabels;
        this.clientService = clientService;
    }

    @GetMapping("/verify")
    public List<BillDtos.BillPaymentDto> verifyQueue() {
        return settlementService.awaitingVerification().stream().map(this::toDto).toList();
    }

    @PostMapping("/verify")
    public List<VendorSettlementService.VerifyOutcome> verify(
            @Valid @RequestBody VerifyRequest request, @AuthenticationPrincipal AuthenticatedUser principal) {
        List<VendorSettlementService.VerifyOutcome> results = new ArrayList<>();
        for (UUID id : request.billPaymentIds()) {
            try {
                results.add(settlementService.verifyOne(id, principal.phoneNumber()));
            } catch (RuntimeException ex) {
                results.add(new VendorSettlementService.VerifyOutcome(id, false, ex.getMessage()));
            }
        }
        return results;
    }

    @GetMapping("/reimburse")
    public List<BillDtos.BillPaymentDto> reimburseQueue() {
        return settlementService.awaitingReimbursement().stream().map(this::toDto).toList();
    }

    @PostMapping("/reimburse")
    public ReimbursementResponse reimburse(
            @Valid @RequestBody ReimburseRequest request, @AuthenticationPrincipal AuthenticatedUser principal) {
        VendorReimbursement saved = settlementService.reimburse(
                request.vendorClientId(), request.billPaymentIds(), request.reference(), principal.phoneNumber());
        return new ReimbursementResponse(saved.getId(), saved.getTotalAmount(), saved.getReference(), request.billPaymentIds().size());
    }

    @GetMapping("/paid")
    public List<VendorSettlementService.PaidPayment> paid(
            @RequestParam(required = false) UUID vendorClientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd) {
        return settlementService.paid(vendorClientId, periodStart, periodEnd);
    }

    @GetMapping("/commission")
    public VendorSettlementService.CommissionPreview commission(
            @RequestParam UUID vendorClientId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd) {
        return settlementService.preview(vendorClientId, periodStart, periodEnd);
    }

    @GetMapping("/commission/export")
    public ResponseEntity<byte[]> exportCommission(
            @RequestParam UUID vendorClientId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd) throws IOException {
        byte[] xlsx = CommissionReportSheet.workbook(settlementService.preview(vendorClientId, periodStart, periodEnd));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"vendor-commission.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }

    @PostMapping("/commission")
    public CycleResponse closeCommission(
            @Valid @RequestBody CloseCycleRequest request, @AuthenticationPrincipal AuthenticatedUser principal) {
        CommissionCycle cycle = settlementService.close(
                request.vendorClientId(), request.periodStart(), request.periodEnd(),
                request.reference(), principal.phoneNumber());
        return new CycleResponse(cycle.getId(), cycle.getTotalAmount(), cycle.getTotalCommission(), cycle.getReceiptReference());
    }

    private BillDtos.BillPaymentDto toDto(BillPayment payment) {
        Bill bill = billRepository.findById(payment.getBillId())
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found"));
        String vendorName = payment.getAssignedVendorClientId() == null
                ? null
                : clientService.getById(payment.getAssignedVendorClientId()).getClientName();
        return BillDtos.BillPaymentDto.from(
                payment, bill, categoryLabels.lookup(), false, false, id -> vendorName);
    }

    public record VerifyRequest(@NotEmpty List<UUID> billPaymentIds) {
    }

    public record ReimburseRequest(
            @NotNull UUID vendorClientId,
            @NotEmpty List<UUID> billPaymentIds,
            @NotBlank @Size(max = 120) String reference) {
    }

    public record CloseCycleRequest(
            @NotNull UUID vendorClientId,
            @NotNull LocalDate periodStart,
            @NotNull LocalDate periodEnd,
            @NotBlank @Size(max = 120) String reference) {
    }

    public record ReimbursementResponse(UUID id, java.math.BigDecimal totalAmount, String reference, int billCount) {
    }

    public record CycleResponse(UUID id, java.math.BigDecimal totalAmount, java.math.BigDecimal totalCommission, String reference) {
    }
}
