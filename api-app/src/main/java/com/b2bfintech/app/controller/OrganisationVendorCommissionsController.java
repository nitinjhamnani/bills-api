package com.b2bfintech.app.controller;

import com.b2bfintech.app.service.VendorSettlementService;
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
import java.util.UUID;

/** Commission still due on bills Paytrix has already paid to a vendor. */
@RestController
@RequestMapping("/api/v1/paytrix/vendor-commissions")
@PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','CHECKER','MAKER','VIEWER')")
public class OrganisationVendorCommissionsController {

    private final VendorSettlementService settlementService;

    public OrganisationVendorCommissionsController(VendorSettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @GetMapping
    public List<VendorSettlementService.CommissionBill> list(
            @RequestParam(required = false) String vendorClientId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd) {
        return settlementService.commissionBills(vendorClientId, periodStart, periodEnd);
    }

    /** Spreadsheet of the same bills the page lists: one sheet of vendor totals, one of each bill. */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String vendorClientId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd) throws IOException {
        List<VendorSettlementService.CommissionBill> bills =
                settlementService.commissionBills(vendorClientId, periodStart, periodEnd);
        String vendorLabel = vendorClientId == null || vendorClientId.isBlank()
                ? "All vendors"
                : bills.stream()
                        .map(VendorSettlementService.CommissionBill::vendorName)
                        .filter(name -> name != null && !name.isBlank())
                        .findFirst()
                        .orElseGet(() -> settlementService.vendorName(vendorClientId));
        byte[] xlsx = VendorCommissionReport.workbook(bills, periodStart, periodEnd, vendorLabel);
        String filename = "vendor-commission-" + periodStart + "-to-" + periodEnd + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','CHECKER')")
    public VendorSettlementService.CommissionResult receive(
            @Valid @RequestBody ReceiveRequest request, @AuthenticationPrincipal AuthenticatedUser principal) {
        return settlementService.receiveSelected(
                request.billPaymentIds(), request.reference(), request.mode(), principal);
    }

    public record ReceiveRequest(
            @NotEmpty List<UUID> billPaymentIds,
            @NotBlank @Size(max = 120) String reference,
            String mode) {
    }
}
