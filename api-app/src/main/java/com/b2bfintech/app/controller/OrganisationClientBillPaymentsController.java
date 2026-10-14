package com.b2bfintech.app.controller;

import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.repository.BillPaymentRepository;
import com.b2bfintech.billing.repository.BillRepository;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.app.service.CategoryLabels;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/** One client's full bill payment history (any status) - backs the client detail page's Bills tab. */
@RestController
@RequestMapping("/api/v1/organisation/clients/{clientId}/bill-payments")
@PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER','VIEWER')")
public class OrganisationClientBillPaymentsController {

    private final BillPaymentRepository billPaymentRepository;
    private final BillRepository billRepository;
    private final CategoryLabels categoryLabels;

    public OrganisationClientBillPaymentsController(
            BillPaymentRepository billPaymentRepository, BillRepository billRepository,
            CategoryLabels categoryLabels) {
        this.billPaymentRepository = billPaymentRepository;
        this.billRepository = billRepository;
        this.categoryLabels = categoryLabels;
    }

    @GetMapping
    public List<BillDtos.BillPaymentDto> list(@PathVariable UUID clientId) {
        Function<String, String> names = categoryLabels.lookup();
        return billPaymentRepository.findByClientIdOrderByCreatedAtDesc(clientId).stream()
                .map(p -> {
                    Bill bill = billRepository.findById(p.getBillId()).orElseThrow(() -> new ResourceNotFoundException("Bill not found"));
                    return BillDtos.BillPaymentDto.from(p, bill, names);
                })
                .toList();
    }
}
