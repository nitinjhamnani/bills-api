package com.b2bfintech.app.controller;

import com.b2bfintech.app.service.ClientAccountGuard;
import com.b2bfintech.app.service.WalletTopupService;
import com.b2bfintech.common.document.DocumentStorageService;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.common.security.ClientScopeGuard;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.wallet.entity.LedgerEntry;
import com.b2bfintech.wallet.entity.WalletAccount;
import com.b2bfintech.wallet.entity.WalletTransactionRequest;
import com.b2bfintech.wallet.repository.LedgerEntryRepository;
import com.b2bfintech.wallet.service.WalletService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
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
    private final ClientAccountGuard clientAccountGuard;
    private final DocumentStorageService storageService;

    public WalletController(
            WalletService walletService, WalletTopupService walletTopupService,
            LedgerEntryRepository ledgerEntryRepository, ClientAccountGuard clientAccountGuard,
            DocumentStorageService storageService) {
        this.walletService = walletService;
        this.walletTopupService = walletTopupService;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.clientAccountGuard = clientAccountGuard;
        this.storageService = storageService;
    }

    @GetMapping
    public BalanceDto balance(@PathVariable String clientId, @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        clientAccountGuard.requireWalletClient(clientId);
        return new BalanceDto(walletService.getBalance(clientId, WalletAccount.WalletType.WALLET));
    }

    @GetMapping("/ledger")
    public List<LedgerEntryDto> ledger(@PathVariable String clientId, @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        clientAccountGuard.requireWalletClient(clientId);
        WalletAccount account = walletService.ensureAccount(clientId, WalletAccount.WalletType.WALLET);
        List<LedgerEntry> newestFirst = ledgerEntryRepository.findByWalletAccountIdOrderByCreatedAtDesc(account.getId());
        List<LedgerEntryDto> rows = new ArrayList<>(newestFirst.size());
        BigDecimal balanceAfter = account.getBalance();
        for (LedgerEntry entry : newestFirst) {
            rows.add(LedgerEntryDto.from(entry, balanceAfter));
            balanceAfter = balanceAfter.subtract(entry.signedAmount());
        }
        return rows;
    }

    @PostMapping(value = "/topup", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CLIENT_ADMIN')")
    public TopupDto topUp(@PathVariable String clientId,
                          @RequestParam(required = false) BigDecimal amount,
                          @RequestParam(required = false) WalletTransactionRequest.Mode paymentMode,
                          @RequestParam(required = false) String paymentReference,
                          @RequestParam(required = false) MultipartFile proof,
                          @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        clientAccountGuard.requireActiveCorporate(clientId);
        return TopupDto.from(walletTopupService.submit(clientId, amount, paymentMode, paymentReference, proof, principal));
    }

    @GetMapping("/topups")
    public List<TopupDto> topups(@PathVariable String clientId, @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        clientAccountGuard.requireWalletClient(clientId);
        return walletTopupService.listForClient(clientId).stream().map(TopupDto::from).toList();
    }

    @GetMapping("/topups/{requestId}/proof")
    public ResponseEntity<InputStreamResource> proof(@PathVariable String clientId, @PathVariable UUID requestId,
                                                     @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        WalletTransactionRequest request = walletTopupService.get(requestId);
        if (!clientId.equals(request.getCorpId())) {
            throw new ResourceNotFoundException("Top-up request not found");
        }
        String fileName = request.getProof().substring(request.getProof().lastIndexOf('/') + 1);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .contentType(MediaTypeFactory.getMediaType(fileName).orElse(MediaType.APPLICATION_OCTET_STREAM))
                .body(new InputStreamResource(storageService.load(request.getProof())));
    }

    public record BalanceDto(BigDecimal walletBalance) {
    }

    /** {@code balanceAfter} and {@code description} are worked out when read. Neither is stored. */
    public record LedgerEntryDto(String entryType, BigDecimal amount, BigDecimal balanceAfter,
                                 String category, String description, String createdBy, Instant createdAt) {
        static LedgerEntryDto from(LedgerEntry e, BigDecimal balanceAfter) {
            return new LedgerEntryDto(e.getEntryType().name(), e.getAmount(), balanceAfter,
                    e.getCategory(), e.describe(), e.getCreatedBy(), e.getCreatedAt());
        }
    }

    /** {@code paymentMode} is the mode column and {@code paymentReference} is the UTR. */
    public record TopupDto(String id, BigDecimal amount, String paymentMode, String paymentReference,
                           boolean hasProof, String status, Instant createdAt, String createdBy,
                           String approvedBy, Instant approvedOn, Instant lastUpdated) {
        static TopupDto from(WalletTransactionRequest r) {
            return new TopupDto(r.getRequestId().toString(), r.getAmount(), r.getMode().name(), r.getUtr(),
                    r.getProof() != null && !r.getProof().isBlank(), r.getStatus().name(), r.getCreatedDate(),
                    r.getCreatedBy(), r.getApprovedBy(), r.getApprovedOn(), r.getLastUpdated());
        }
    }
}
