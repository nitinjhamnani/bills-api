package com.b2bfintech.app.controller;

import com.b2bfintech.app.service.DisputeService;
import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillDispute;
import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.billing.repository.BillPaymentRepository;
import com.b2bfintech.billing.repository.BillRepository;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.organisation.repository.ClientRepository;
import com.b2bfintech.app.service.CategoryLabels;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/** Customer-disputed bill payments: clients raise them, organisation admins/checkers resolve them (implementation plan &sect;8). */
@RestController
public class DisputeController {

    private final DisputeService disputeService;
    private final BillPaymentRepository billPaymentRepository;
    private final BillRepository billRepository;
    private final ClientRepository clientRepository;
    private final CategoryLabels categoryLabels;

    public DisputeController(DisputeService disputeService, BillPaymentRepository billPaymentRepository,
                              BillRepository billRepository, ClientRepository clientRepository,
                              CategoryLabels categoryLabels) {
        this.disputeService = disputeService;
        this.billPaymentRepository = billPaymentRepository;
        this.billRepository = billRepository;
        this.clientRepository = clientRepository;
        this.categoryLabels = categoryLabels;
    }

    @PostMapping("/api/v1/bill-payments/{billPaymentId}/disputes")
    @PreAuthorize("hasAnyRole('CLIENT_ADMIN','CLIENT_OPERATOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public DisputeDto raise(@PathVariable UUID billPaymentId, @RequestBody RaiseRequest request,
                             @AuthenticationPrincipal AuthenticatedUser principal) {
        return toDto(disputeService.raise(billPaymentId, request.reason(), principal.phoneNumber()));
    }

    @GetMapping("/api/v1/bill-payments/{billPaymentId}/disputes")
    public List<DisputeDto> listForBillPayment(@PathVariable UUID billPaymentId) {
        return disputeService.listForBillPayment(billPaymentId).stream().map(this::toDto).toList();
    }

    @GetMapping("/api/v1/organisation/disputes")
    @PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','CHECKER','MAKER','VIEWER')")
    public List<DisputeDto> listAll() {
        return disputeService.listAll().stream().map(this::toDto).toList();
    }

    @PostMapping("/api/v1/organisation/disputes/{id}/resolve")
    @PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','CHECKER')")
    public DisputeDto resolve(@PathVariable UUID id, @RequestBody ResolveRequest request,
                               @AuthenticationPrincipal AuthenticatedUser principal) {
        return toDto(disputeService.resolve(id, request.valid(), request.notes(), principal.phoneNumber()));
    }

    private DisputeDto toDto(BillDispute d) {
        BillPayment payment = billPaymentRepository.findById(d.getBillPaymentId()).orElse(null);
        BillDtos.BillPaymentDto billPaymentDto = null;
        String clientId = null;
        String clientName = null;
        String clientCode = null;
        if (payment != null) {
            Bill bill = billRepository.findById(payment.getBillId()).orElse(null);
            if (bill != null) {
                Function<String, String> names = categoryLabels.lookup();
                billPaymentDto = BillDtos.BillPaymentDto.from(payment, bill, names);
            }
            clientId = payment.getClientId().toString();
            var client = clientRepository.findById(payment.getClientId()).orElse(null);
            clientName = client != null ? client.getClientName() : "Unknown client";
            clientCode = client != null ? client.getClientCode() : null;
        }
        return new DisputeDto(d.getId().toString(), d.getBillPaymentId().toString(), d.getReason(),
                d.getStatus().name(), d.getResolutionNotes(), d.getRaisedBy(), d.getResolvedBy(),
                d.getCreatedAt(), clientId, clientName, clientCode, billPaymentDto);
    }

    public record RaiseRequest(@NotBlank String reason) {
    }

    public record ResolveRequest(boolean valid, String notes) {
    }

    public record DisputeDto(String id, String billPaymentId, String reason, String status, String resolutionNotes,
                              String raisedBy, String resolvedBy, Instant createdAt,
                              String clientId, String clientName, String clientCode, BillDtos.BillPaymentDto billPayment) {
    }
}
