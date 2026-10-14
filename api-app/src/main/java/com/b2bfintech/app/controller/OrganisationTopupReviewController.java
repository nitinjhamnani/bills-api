package com.b2bfintech.app.controller;

import com.b2bfintech.app.service.MakerCheckerPolicy;
import com.b2bfintech.app.service.WalletTopupService;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.repository.ClientRepository;
import com.b2bfintech.wallet.entity.WalletTopupRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Organisation review of wallet top-up requests across all clients - always a single accept/reject
 * before the client's wallet is credited (implementation plan &sect;4.2/&sect;6). */
@RestController
@RequestMapping("/api/v1/organisation/wallet-topups")
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
        List<WalletTopupRequest> requests = walletTopupService.listPending();
        List<Client> clients = clientRepository.findAllById(
                requests.stream().map(WalletTopupRequest::getClientId).distinct().toList());
        Map<UUID, String> clientNames = clients.stream().collect(Collectors.toMap(Client::getId, Client::getClientName));
        Map<UUID, String> clientCodes = clients.stream()
                .collect(Collectors.toMap(Client::getId, c -> c.getClientCode() != null ? c.getClientCode() : ""));
        return requests.stream()
                .map(r -> PendingTopupDto.from(r, clientNames.getOrDefault(r.getClientId(), "Unknown"),
                        clientCodes.getOrDefault(r.getClientId(), ""),
                        makerCheckerPolicy.canReviewTopup(principal, r.getAmount())))
                .toList();
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('ORGANISATION_ADMIN','MAKER','CHECKER')")
    public WalletController.TopupDto review(
            @PathVariable UUID id, @RequestBody ReviewRequest request, @AuthenticationPrincipal AuthenticatedUser principal) {
        WalletTopupRequest result = walletTopupService.reviewTopupRequest(id, request.approve(), principal);
        return WalletController.TopupDto.from(result);
    }

    public record ReviewRequest(boolean approve) {
    }

    public record PendingTopupDto(String id, String clientId, String clientName, String clientCode, BigDecimal amount,
                                   String paymentMode, String paymentReference, UUID documentId, String status,
                                   Instant createdAt, boolean canReview) {
        static PendingTopupDto from(WalletTopupRequest r, String clientName, String clientCode, boolean canReview) {
            return new PendingTopupDto(r.getId().toString(), r.getClientId().toString(), clientName, clientCode, r.getAmount(),
                    r.getPaymentMode().name(), r.getPaymentReference(), r.getDocumentId(), r.getStatus().name(),
                    r.getCreatedAt(), canReview);
        }
    }
}
