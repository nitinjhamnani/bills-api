package com.b2bfintech.app.controller;

import com.b2bfintech.app.service.MakerCheckerPolicy;
import com.b2bfintech.app.service.WalletTopupService;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.repository.ClientRepository;
import com.b2bfintech.wallet.entity.WalletTransactionRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Paytrix review of wallet top-up requests across all corporates. Approving credits the wallet. */
@RestController
@RequestMapping("/api/v1/paytrix/wallet-topups")
@PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER','VIEWER')")
public class OrganisationTopupReviewController {

    private final WalletTopupService walletTopupService;
    private final ClientRepository clientRepository;
    private final MakerCheckerPolicy makerCheckerPolicy;

    public OrganisationTopupReviewController(WalletTopupService walletTopupService, ClientRepository clientRepository,
                                              MakerCheckerPolicy makerCheckerPolicy) {
        this.walletTopupService = walletTopupService;
        this.clientRepository = clientRepository;
        this.makerCheckerPolicy = makerCheckerPolicy;
    }

    @GetMapping("/pending")
    public List<PendingTopupDto> pending(@AuthenticationPrincipal AuthenticatedUser principal) {
        List<WalletTransactionRequest> requests = walletTopupService.listPending();
        List<Client> clients = clientRepository.findAllById(
                requests.stream().map(WalletTransactionRequest::getCorpId).distinct().toList());
        Map<String, String> clientNames = clients.stream().collect(Collectors.toMap(Client::getId, Client::getClientName));
        Map<String, String> clientCodes = clients.stream()
                .collect(Collectors.toMap(Client::getId, c -> c.getClientCode() != null ? c.getClientCode() : ""));
        return requests.stream()
                .map(r -> PendingTopupDto.from(r, clientNames.getOrDefault(r.getCorpId(), "Unknown"),
                        clientCodes.getOrDefault(r.getCorpId(), ""),
                        makerCheckerPolicy.canReviewTopup(principal, r.getAmount())))
                .toList();
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER')")
    public WalletController.TopupDto review(
            @PathVariable UUID id, @RequestBody ReviewRequest request, @AuthenticationPrincipal AuthenticatedUser principal) {
        return WalletController.TopupDto.from(walletTopupService.review(id, request.approve(), principal));
    }

    public record ReviewRequest(boolean approve) {
    }

    public record PendingTopupDto(String id, String clientId, String clientName, String clientCode, BigDecimal amount,
                                  String paymentMode, String paymentReference, boolean hasProof, String status,
                                  Instant createdAt, String createdBy, boolean canReview) {
        static PendingTopupDto from(WalletTransactionRequest r, String clientName, String clientCode, boolean canReview) {
            return new PendingTopupDto(r.getRequestId().toString(), r.getCorpId(), clientName, clientCode, r.getAmount(),
                    r.getMode().name(), r.getUtr(), r.getProof() != null && !r.getProof().isBlank(), r.getStatus().name(),
                    r.getCreatedDate(), r.getCreatedBy(), canReview);
        }
    }
}
