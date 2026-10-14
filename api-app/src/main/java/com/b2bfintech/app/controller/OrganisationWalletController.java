package com.b2bfintech.app.controller;

import com.b2bfintech.common.audit.AuditService;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.repository.ClientRepository;
import com.b2bfintech.wallet.entity.LedgerEntry;
import com.b2bfintech.wallet.entity.WalletAccount;
import com.b2bfintech.wallet.service.WalletService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.b2bfintech.common.security.AuthenticatedUser;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The organisation's "Manage Wallets" view: every client's balance at a glance, plus letting an
 * organisation admin credit a client's wallet directly (e.g. a goodwill credit or an offline
 * payment collected outside the top-up-request flow) without needing the client to submit a
 * top-up request first. Accepting/rejecting an actual top-up request stays on the existing
 * {@code /api/v1/organisation/wallet-topups} endpoints; per-client balance/ledger stays on the
 * existing {@code /api/v1/clients/{clientId}/wallet} endpoints, which organisation roles can
 * already call.
 */
@RestController
@RequestMapping("/api/v1/organisation/wallets")
@PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER','VIEWER')")
public class OrganisationWalletController {

    private final ClientRepository clientRepository;
    private final WalletService walletService;
    private final AuditService auditService;

    public OrganisationWalletController(ClientRepository clientRepository, WalletService walletService, AuditService auditService) {
        this.clientRepository = clientRepository;
        this.walletService = walletService;
        this.auditService = auditService;
    }

    @GetMapping
    public List<ClientWalletDto> list() {
        return clientRepository.findAll().stream()
                .map(c -> new ClientWalletDto(
                        c.getId().toString(), c.getClientName(), c.getClientCode(),
                        walletService.getBalance(c.getId(), WalletAccount.WalletType.WALLET)))
                .toList();
    }

    /** Direct admin credit - org admin only, since this moves money with no counter-approval. */
    @PostMapping("/{clientId}/credit")
    @PreAuthorize("hasRole('ORGANISATION_ADMIN')")
    public ClientWalletDto addFunds(@PathVariable UUID clientId, @Valid @RequestBody AddFundsRequest request,
                                     @AuthenticationPrincipal AuthenticatedUser principal) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new BadRequestException("Unknown client"));
        walletService.credit(clientId, WalletAccount.WalletType.WALLET, request.amount(),
                LedgerEntry.ReferenceType.ADJUSTMENT, null, null,
                "Manual credit by " + principal.phoneNumber() + ": " + request.note());
        auditService.record("WALLET_MANUAL_CREDIT", "Client", clientId.toString(),
                "credited " + request.amount() + " - " + request.note());
        return new ClientWalletDto(client.getId().toString(), client.getClientName(), client.getClientCode(),
                walletService.getBalance(clientId, WalletAccount.WalletType.WALLET));
    }

    public record AddFundsRequest(@NotNull @Positive BigDecimal amount, @NotBlank String note) {
    }

    public record ClientWalletDto(String clientId, String clientName, String clientCode, BigDecimal balance) {
    }
}
