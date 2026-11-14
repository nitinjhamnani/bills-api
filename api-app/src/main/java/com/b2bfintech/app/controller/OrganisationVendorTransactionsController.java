package com.b2bfintech.app.controller;

import com.b2bfintech.app.service.VendorSettlementService;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.service.ClientService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Every verified bill for one vendor: what Paytrix owes or has paid, and the commission. */
@RestController
@RequestMapping("/api/v1/paytrix/clients/{clientId}/transactions")
@PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER','VIEWER')")
public class OrganisationVendorTransactionsController {

    private final ClientService clientService;
    private final VendorSettlementService vendorSettlementService;

    public OrganisationVendorTransactionsController(
            ClientService clientService, VendorSettlementService vendorSettlementService) {
        this.clientService = clientService;
        this.vendorSettlementService = vendorSettlementService;
    }

    @GetMapping
    public List<VendorSettlementService.VendorTransactionRow> list(@PathVariable String clientId) {
        Client vendor = clientService.getById(clientId);
        if (vendor.getClientType() != Client.ClientType.VENDOR) {
            throw new BadRequestException("Transactions on this tab are listed for a vendor");
        }
        return vendorSettlementService.transactions(clientId);
    }
}
