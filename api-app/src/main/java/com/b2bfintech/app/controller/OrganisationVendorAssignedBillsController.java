package com.b2bfintech.app.controller;

import com.b2bfintech.app.service.CategoryLabels;
import com.b2bfintech.app.service.VendorPaymentService;
import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.billing.entity.VendorPayment;
import com.b2bfintech.billing.repository.BillRepository;
import com.b2bfintech.billing.repository.VendorPaymentRepository;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.service.ClientService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Bills a corporate paid and Paytrix assigned to this vendor. A vendor does not own bills. */
@RestController
@RequestMapping("/api/v1/paytrix/clients/{clientId}/assigned-bills")
@PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER','VIEWER')")
public class OrganisationVendorAssignedBillsController {

    private final ClientService clientService;
    private final VendorPaymentService vendorPaymentService;
    private final BillRepository billRepository;
    private final CategoryLabels categoryLabels;
    private final VendorPaymentRepository vendorPaymentRepository;

    public OrganisationVendorAssignedBillsController(
            ClientService clientService, VendorPaymentService vendorPaymentService,
            BillRepository billRepository, CategoryLabels categoryLabels,
            VendorPaymentRepository vendorPaymentRepository) {
        this.clientService = clientService;
        this.vendorPaymentService = vendorPaymentService;
        this.billRepository = billRepository;
        this.categoryLabels = categoryLabels;
        this.vendorPaymentRepository = vendorPaymentRepository;
    }

    @GetMapping
    public List<BillDtos.BillPaymentDto> list(@PathVariable String clientId) {
        Client vendor = clientService.getById(clientId);
        if (vendor.getClientType() != Client.ClientType.VENDOR) {
            throw new BadRequestException("Assigned bills are listed for a vendor");
        }
        return vendorPaymentService.assigned(clientId).stream().map(this::toDto).toList();
    }

    private BillDtos.BillPaymentDto toDto(BillPayment payment) {
        Bill bill = billRepository.findById(payment.getBillId())
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found"));
        BillDtos.BillPaymentDto dto = BillDtos.BillPaymentDto.from(
                payment, bill, categoryLabels.lookup(), false, false, id -> payment.getBillAssignedToName(),
                null, null);
        return vendorPaymentRepository.findByBillPaymentId(payment.getId())
                .filter(owed -> owed.getPaymentStatus() == VendorPayment.PaymentStatus.PAID)
                .map(owed -> dto.withReimbursedAt(owed.getPaymentMadeOn()))
                .orElse(dto);
    }
}
