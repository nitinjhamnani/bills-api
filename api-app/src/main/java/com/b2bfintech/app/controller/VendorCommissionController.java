package com.b2bfintech.app.controller;

import com.b2bfintech.app.service.CategoryLabels;
import com.b2bfintech.app.service.ClientAccountGuard;
import com.b2bfintech.app.service.VendorPaymentService;
import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.billing.repository.BillRepository;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.service.ClientService;
import com.b2bfintech.organisation.service.CommissionRuleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/client")
@PreAuthorize("hasAnyRole('CLIENT_ADMIN','CLIENT_OPERATOR')")
public class VendorCommissionController {

    private final VendorPaymentService vendorPaymentService;
    private final ClientAccountGuard clientAccountGuard;
    private final ClientService clientService;
    private final BillRepository billRepository;
    private final CategoryLabels categoryLabels;

    public VendorCommissionController(
            VendorPaymentService vendorPaymentService,
            ClientAccountGuard clientAccountGuard, ClientService clientService,
            BillRepository billRepository, CategoryLabels categoryLabels) {
        this.vendorPaymentService = vendorPaymentService;
        this.clientAccountGuard = clientAccountGuard;
        this.clientService = clientService;
        this.billRepository = billRepository;
        this.categoryLabels = categoryLabels;
    }

    @GetMapping("/commission-rules")
    public List<CommissionRuleService.VendorRuleView> rules(@AuthenticationPrincipal AuthenticatedUser principal) {
        throw new AccessDeniedException("A vendor cannot view commissions");
    }

    @PostMapping("/commission-rules/{id}/proposals")
    @PreAuthorize("hasRole('CLIENT_ADMIN')")
    public CommissionRuleService.VendorRuleView propose(
            @PathVariable UUID id, @Valid @RequestBody ProposeRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        throw new AccessDeniedException("A vendor cannot view commissions");
    }

    @GetMapping("/assigned-bills")
    public List<BillDtos.BillPaymentDto> assigned(@AuthenticationPrincipal AuthenticatedUser principal) {
        Client vendor = vendor(principal);
        return vendorPaymentService.assigned(vendor.getId()).stream()
                .map(payment -> toDto(payment, vendor.getClientName()))
                .toList();
    }

    @PostMapping("/assigned-bills/{id}/in-progress")
    @PreAuthorize("hasRole('CLIENT_ADMIN')")
    public BillDtos.BillPaymentDto markInProgress(
            @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
        Client vendor = clientAccountGuard.requireActiveVendor(vendor(principal).getId());
        BillPayment payment = vendorPaymentService.markInProgress(vendor.getId(), id, principal.phoneNumber());
        return toDto(payment, vendor.getClientName());
    }

    @GetMapping("/assigned-bills/export")
    @PreAuthorize("hasRole('CLIENT_ADMIN')")
    public ResponseEntity<byte[]> exportAssigned(@AuthenticationPrincipal AuthenticatedUser principal) throws IOException {
        Client vendor = clientAccountGuard.requireActiveVendor(vendor(principal).getId());
        List<BillDtos.BillPaymentDto> rows = vendorPaymentService.sheetBills(vendor.getId()).stream()
                .map(payment -> toDto(payment, vendor.getClientName()))
                .toList();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"assigned-bills.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(VendorBillSheet.workbook(rows));
    }

    @PostMapping(value = "/assigned-bills/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CLIENT_ADMIN')")
    public SheetPreview previewImport(
            @RequestParam MultipartFile file, @AuthenticationPrincipal AuthenticatedUser principal) throws IOException {
        Client vendor = clientAccountGuard.requireActiveVendor(vendor(principal).getId());
        int inProgress = 0;
        int paid = 0;
        int unchanged = 0;
        List<SheetRowNote> errors = new ArrayList<>();
        for (VendorBillSheet.RawRow row : VendorBillSheet.read(file.getInputStream())) {
            VendorPaymentService.SheetDecision decision = vendorPaymentService.decide(
                    vendor.getId(), row.paymentId(), row.status(), row.reference());
            if (decision.error() != null) {
                errors.add(new SheetRowNote(row.row(), decision.error()));
            } else if ("IN_PROGRESS".equals(decision.action())) {
                inProgress++;
            } else if ("PAID".equals(decision.action())) {
                paid++;
            } else {
                unchanged++;
            }
        }
        return new SheetPreview(inProgress, paid, unchanged, errors);
    }

    @PostMapping(value = "/assigned-bills/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CLIENT_ADMIN')")
    public List<BillDtos.BulkRowResult> importAssigned(
            @RequestParam MultipartFile file, @AuthenticationPrincipal AuthenticatedUser principal) throws IOException {
        Client vendor = clientAccountGuard.requireActiveVendor(vendor(principal).getId());
        List<BillDtos.BulkRowResult> results = new ArrayList<>();
        for (VendorBillSheet.RawRow row : VendorBillSheet.read(file.getInputStream())) {
            VendorPaymentService.SheetDecision decision = vendorPaymentService.decide(
                    vendor.getId(), row.paymentId(), row.status(), row.reference());
            if (decision.error() != null) {
                results.add(new BillDtos.BulkRowResult(row.row(), false, row.paymentId(), decision.error()));
                continue;
            }
            if (decision.action() == null) {
                continue;
            }
            try {
                if ("IN_PROGRESS".equals(decision.action())) {
                    vendorPaymentService.markInProgress(vendor.getId(), UUID.fromString(row.paymentId().trim()), principal.phoneNumber());
                } else {
                    vendorPaymentService.markPaidFromSheet(
                            vendor.getId(), UUID.fromString(row.paymentId().trim()), row.reference(), principal.phoneNumber());
                }
                results.add(new BillDtos.BulkRowResult(row.row(), true, row.paymentId(), null));
            } catch (RuntimeException ex) {
                results.add(new BillDtos.BulkRowResult(row.row(), false, row.paymentId(), ex.getMessage()));
            }
        }
        return results;
    }

    @PostMapping("/assigned-bills/{id}/mark-paid")
    @PreAuthorize("hasRole('CLIENT_ADMIN')")
    public BillDtos.BillPaymentDto markPaid(
            @PathVariable UUID id, @Valid @RequestBody MarkPaidRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        Client vendor = clientAccountGuard.requireActiveVendor(vendor(principal).getId());
        BillPayment payment = vendorPaymentService.markPaid(vendor.getId(), id, request.reference(), principal.phoneNumber());
        return toDto(payment, vendor.getClientName());
    }

    @PostMapping("/assigned-bills/reject")
    @PreAuthorize("hasRole('CLIENT_ADMIN')")
    public List<BillDtos.BillPaymentDto> rejectMany(
            @Valid @RequestBody RejectManyRequest request, @AuthenticationPrincipal AuthenticatedUser principal) {
        Client vendor = clientAccountGuard.requireActiveVendor(vendor(principal).getId());
        return vendorPaymentService
                .rejectMany(vendor.getId(), request.billPaymentIds(), request.reason(), principal.phoneNumber())
                .stream()
                .map(payment -> toDto(payment, assignedName(payment)))
                .toList();
    }

    @PostMapping("/assigned-bills/{id}/reject")
    @PreAuthorize("hasRole('CLIENT_ADMIN')")
    public BillDtos.BillPaymentDto reject(
            @PathVariable UUID id, @Valid @RequestBody RejectRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        Client vendor = clientAccountGuard.requireActiveVendor(vendor(principal).getId());
        BillPayment payment = vendorPaymentService.reject(vendor.getId(), id, request.reason(), principal.phoneNumber());
        return toDto(payment, assignedName(payment));
    }

    private BillDtos.BillPaymentDto toDto(BillPayment payment, String vendorName) {
        Bill bill = billRepository.findById(payment.getBillId())
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found"));
        return BillDtos.BillPaymentDto.from(
                payment, bill, categoryLabels.lookup(), false, false, id -> vendorName,
                null, null);
    }

    private Client vendor(AuthenticatedUser principal) {
        if (principal.clientId() == null || principal.clientId().isBlank()) {
            throw new BadRequestException("This action is only available to a vendor");
        }
        Client client = clientService.getById(UUID.fromString(principal.clientId()));
        if (client.getClientType() != Client.ClientType.VENDOR) {
            throw new BadRequestException("Only a vendor can do this");
        }
        return client;
    }

    public record ProposeRequest(
            @NotNull @DecimalMin("0.001") @DecimalMax("100") BigDecimal commissionPercent) {
    }

    public record MarkPaidRequest(@NotBlank @Size(max = 120) String reference) {
    }

    public record RejectRequest(@NotBlank @Size(max = 300) String reason) {
    }

    public record RejectManyRequest(
            @NotEmpty List<UUID> billPaymentIds,
            @NotBlank @Size(max = 300) String reason) {
    }

    public record SheetPreview(int inProgress, int paid, int unchanged, List<SheetRowNote> errors) {
    }

    public record SheetRowNote(int row, String message) {
    }

    private String assignedName(BillPayment payment) {
        if (payment.getAssignedVendorClientId() == null) {
            return null;
        }
        return clientService.getById(payment.getAssignedVendorClientId()).getClientName();
    }
}
