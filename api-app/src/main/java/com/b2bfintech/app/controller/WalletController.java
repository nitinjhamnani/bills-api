package com.b2bfintech.app.controller;

import com.b2bfintech.app.service.WalletTopupService;
import com.b2bfintech.common.security.ClientScopeGuard;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.wallet.entity.LedgerEntry;
import com.b2bfintech.wallet.entity.WalletAccount;
import com.b2bfintech.wallet.entity.WalletTopupRequest;
import com.b2bfintech.wallet.repository.LedgerEntryRepository;
import com.b2bfintech.wallet.service.WalletService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Client wallet: balance, ledger history, top-up requests (implementation plan &sect;4.3 /
 * &sect;6). Reachable by the client itself (scoped to its own id via {@link ClientScopeGuard})
 * and by any organisation-side role (full visibility across all its clients, per &sect;4.2).
 */
@RestController
@RequestMapping("/api/v1/clients/{clientId}/wallet")
@PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER','VIEWER','CLIENT_ADMIN','CLIENT_OPERATOR')")
public class WalletController {

    private final WalletService walletService;
    private final WalletTopupService walletTopupService;
    private final LedgerEntryRepository ledgerEntryRepository;

    public WalletController(WalletService walletService, WalletTopupService walletTopupService, LedgerEntryRepository ledgerEntryRepository) {
        this.walletService = walletService;
        this.walletTopupService = walletTopupService;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @GetMapping
    public BalanceDto balance(@PathVariable UUID clientId, @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        return new BalanceDto(walletService.getBalance(clientId, WalletAccount.WalletType.WALLET));
    }

    @GetMapping("/ledger")
    public List<LedgerEntryDto> ledger(@PathVariable UUID clientId, @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        WalletAccount account = walletService.ensureAccount(clientId, WalletAccount.WalletType.WALLET);
        return ledgerEntryRepository.findByWalletAccountIdOrderByCreatedAtDesc(account.getId()).stream()
                .map(LedgerEntryDto::from).toList();
    }

    @PostMapping("/topup")
    @PreAuthorize("hasRole('CLIENT_ADMIN')")
    public TopupDto topUp(@PathVariable UUID clientId, @Valid @RequestBody TopupRequest request,
                           @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        var result = walletTopupService.submitTopupRequest(clientId, request.amount(), request.paymentMode(),
                request.paymentReference(), request.documentId(), request.idempotencyKey(), principal.phoneNumber());
        return TopupDto.from(result);
    }

    @GetMapping("/topups")
    public List<TopupDto> topups(@PathVariable UUID clientId, @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        return walletTopupService.listForClient(clientId).stream().map(TopupDto::from).toList();
    }

    public record TopupRequest(@NotNull @Positive BigDecimal amount, @NotNull WalletTopupRequest.PaymentMode paymentMode,
                                @NotBlank String paymentReference, UUID documentId, String idempotencyKey) {
    }

    public record BalanceDto(BigDecimal walletBalance) {
    }

    public record LedgerEntryDto(String entryType, BigDecimal amount, BigDecimal balanceAfter,
                                  String referenceType, String referenceId, String description, Instant createdAt) {
        static LedgerEntryDto from(LedgerEntry e) {
            return new LedgerEntryDto(e.getEntryType().name(), e.getAmount(), e.getBalanceAfter(),
                    e.getReferenceType().name(), e.getReferenceId(), e.getDescription(), e.getCreatedAt());
        }
    }

    public record TopupDto(String id, BigDecimal amount, String paymentMode, String paymentReference,
                            UUID documentId, String status, Instant createdAt) {
        static TopupDto from(WalletTopupRequest r) {
            return new TopupDto(r.getId().toString(), r.getAmount(), r.getPaymentMode().name(),
                    r.getPaymentReference(), r.getDocumentId(), r.getStatus().name(), r.getCreatedAt());
        }
    }
}
