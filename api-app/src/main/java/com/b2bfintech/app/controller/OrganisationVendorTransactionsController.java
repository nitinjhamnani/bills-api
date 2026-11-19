package com.b2bfintech.app.controller;

import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.organisation.entity.AppUser;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.repository.AppUserRepository;
import com.b2bfintech.organisation.service.ClientService;
import com.b2bfintech.wallet.entity.VendorTransaction;
import com.b2bfintech.wallet.repository.VendorTransactionRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Every row from vendor_transaction for one vendor. */
@RestController
@RequestMapping("/api/v1/paytrix/clients/{clientId}/transactions")
@PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER','VIEWER')")
public class OrganisationVendorTransactionsController {

    private final ClientService clientService;
    private final VendorTransactionRepository vendorTransactionRepository;
    private final AppUserRepository appUserRepository;

    public OrganisationVendorTransactionsController(
            ClientService clientService,
            VendorTransactionRepository vendorTransactionRepository,
            AppUserRepository appUserRepository) {
        this.clientService = clientService;
        this.vendorTransactionRepository = vendorTransactionRepository;
        this.appUserRepository = appUserRepository;
    }

    @GetMapping
    public List<VendorTransactionDto> list(@PathVariable String clientId) {
        Client vendor = clientService.getById(clientId);
        if (vendor.getClientType() != Client.ClientType.VENDOR) {
            throw new BadRequestException("Transactions on this tab are listed for a vendor");
        }
        List<VendorTransaction> rows = vendorTransactionRepository.findByVendorIdOrderByCreatedAtDesc(clientId);
        Set<String> userIds = rows.stream()
                .map(VendorTransaction::getCreatedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, String> userNames = appUserRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(AppUser::getUserId, AppUser::getName, (a, b) -> a));
        return rows.stream()
                .map(row -> new VendorTransactionDto(
                        row.getId(),
                        row.getVendorId(),
                        row.getType().name(),
                        row.getCategory().name(),
                        row.getAmount(),
                        row.getCreatedBy(),
                        row.getCreatedBy() == null ? null : userNames.get(row.getCreatedBy()),
                        row.getCreatedAt()))
                .toList();
    }

    public record VendorTransactionDto(
            UUID id,
            String vendorId,
            String type,
            String category,
            BigDecimal amount,
            String createdBy,
            String createdByName,
            Instant createdAt) {
    }
}
