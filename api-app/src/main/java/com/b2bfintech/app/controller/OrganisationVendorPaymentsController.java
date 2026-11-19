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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    public List<BillDtos.BillPaymentDto> list(
            @RequestParam(required = false) String vendorClientId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd) {
        return payable(vendorClientId, periodStart, periodEnd).stream().map(this::toDto).toList();
    }

    /** Spreadsheet of the bills Paytrix still has to pay the vendor for in the selected period. */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String vendorClientId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd) throws IOException {
        List<BillPayment> payments = payable(vendorClientId, periodStart, periodEnd);
        String vendorLabel = vendorClientId == null || vendorClientId.isBlank()
                ? "All vendors"
                : payments.stream()
                        .map(BillPayment::getBillAssignedToName)
                        .filter(name -> name != null && !name.isBlank())
                        .findFirst()
                        .orElseGet(() -> settlementService.vendorName(vendorClientId));
        Map<String, Bill> bills = payments.isEmpty()
                ? Map.of()
                : billRepository.findAllById(payments.stream().map(BillPayment::getBillId).collect(Collectors.toSet()))
                        .stream()
                        .collect(Collectors.toMap(Bill::getId, Function.identity()));
        byte[] xlsx = VendorPaymentReport.workbook(payments, bills, periodStart, periodEnd, vendorLabel);
        String filename = "vendor-payment-" + periodStart + "-to-" + periodEnd + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }

    private List<BillPayment> payable(String vendorClientId, LocalDate periodStart, LocalDate periodEnd) {
        return settlementService.awaitingReimbursement(vendorClientId, periodStart, periodEnd);
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
