package com.b2bfintech.app.controller;

import com.b2bfintech.app.service.BillActivityLogService;
import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.billing.repository.BillPaymentRepository;
import com.b2bfintech.billing.repository.BillRepository;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.organisation.repository.ClientRepository;
import com.b2bfintech.app.service.CategoryLabels;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Read-only, tenant-wide view of every client's Managed Bills - lets an organisation user browse
 * (optionally filtered to one client) the same bill/bill-trail/activity-log detail the client
 * itself sees, without any of the create/automation actions (those stay client-owned).
 */
@RestController
@RequestMapping("/api/v1/organisation/managed-bills")
@PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER','VIEWER')")
public class OrganisationManagedBillsController {

    private final BillRepository billRepository;
    private final BillPaymentRepository billPaymentRepository;
    private final ClientRepository clientRepository;
    private final BillActivityLogService activityLogService;
    private final CategoryLabels categoryLabels;

    public OrganisationManagedBillsController(
            BillRepository billRepository, BillPaymentRepository billPaymentRepository, ClientRepository clientRepository,
            BillActivityLogService activityLogService, CategoryLabels categoryLabels) {
        this.billRepository = billRepository;
        this.billPaymentRepository = billPaymentRepository;
        this.clientRepository = clientRepository;
        this.activityLogService = activityLogService;
        this.categoryLabels = categoryLabels;
    }

    /** Every client's managed bills, or just one client's if {@code clientId} is given. */
    @GetMapping
    public List<BillDtos.ManagedBillDto> list(@RequestParam(required = false) UUID clientId) {
        Function<String, String> serviceNames = categoryLabels.lookup();
        Function<UUID, String> clientNames = BillDtos.clientNameLookup(clientRepository);
        Function<UUID, String> clientCodes = BillDtos.clientCodeLookup(clientRepository);
        List<Bill> bills = clientId != null
                ? billRepository.findByClientIdOrderByCreatedAtDesc(clientId)
                : billRepository.findAllByOrderByCreatedAtDesc();
        return bills.stream().map(b -> BillDtos.ManagedBillDto.from(b, serviceNames, clientNames, clientCodes)).toList();
    }

    @GetMapping("/{billId}")
    public BillDtos.ManagedBillDto get(@PathVariable UUID billId) {
        return toDto(getOrThrow(billId));
    }

    @GetMapping("/{billId}/payments")
    public List<BillDtos.BillPaymentDto> payments(@PathVariable UUID billId) {
        Bill bill = getOrThrow(billId);
        Function<String, String> names = categoryLabels.lookup();
        return billPaymentRepository.findByBillIdOrderByCreatedAtDesc(billId).stream()
                .map(p -> BillDtos.BillPaymentDto.from(p, bill, names))
                .toList();
    }

    @GetMapping("/{billId}/payments/{paymentId}")
    public BillDtos.BillPaymentDto payment(@PathVariable UUID billId, @PathVariable UUID paymentId) {
        Bill bill = getOrThrow(billId);
        BillPayment payment = getOwnedPayment(bill, paymentId);
        return BillDtos.BillPaymentDto.from(payment, bill, categoryLabels.lookup());
    }

    @GetMapping("/{billId}/payments/{paymentId}/activity-log")
    public List<BillDtos.ActivityLogDto> paymentActivityLog(@PathVariable UUID billId, @PathVariable UUID paymentId) {
        Bill bill = getOrThrow(billId);
        getOwnedPayment(bill, paymentId); // 404s if not this bill's
        return activityLogService.listForBillPayment(paymentId).stream().map(BillDtos.ActivityLogDto::from).toList();
    }

    private Bill getOrThrow(UUID billId) {
        return billRepository.findById(billId).orElseThrow(() -> new ResourceNotFoundException("Bill not found"));
    }

    private BillPayment getOwnedPayment(Bill bill, UUID paymentId) {
        BillPayment payment = billPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill payment not found"));
        if (!payment.getBillId().equals(bill.getId())) {
            throw new ResourceNotFoundException("Bill payment not found");
        }
        return payment;
    }

    private BillDtos.ManagedBillDto toDto(Bill bill) {
        return BillDtos.ManagedBillDto.from(
                bill, categoryLabels.lookup(),
                BillDtos.clientNameLookup(clientRepository), BillDtos.clientCodeLookup(clientRepository));
    }
}
