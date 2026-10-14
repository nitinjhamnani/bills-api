package com.b2bfintech.app.service;

import com.b2bfintech.common.audit.AuditService;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.wallet.entity.LedgerEntry;
import com.b2bfintech.wallet.entity.WalletAccount;
import com.b2bfintech.wallet.entity.WalletTopupRequest;
import com.b2bfintech.wallet.repository.WalletTopupRequestRepository;
import com.b2bfintech.wallet.service.WalletService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** A client's wallet top-up: one request type, always sitting {@code PENDING} until the
 * organisation accepts (credits the wallet) or rejects it. */
@Service
public class WalletTopupService {

    private final WalletTopupRequestRepository repository;
    private final WalletService walletService;
    private final AuditService auditService;
    private final MakerCheckerPolicy makerCheckerPolicy;

    public WalletTopupService(WalletTopupRequestRepository repository, WalletService walletService,
                               AuditService auditService, MakerCheckerPolicy makerCheckerPolicy) {
        this.repository = repository;
        this.walletService = walletService;
        this.auditService = auditService;
        this.makerCheckerPolicy = makerCheckerPolicy;
    }

    @Transactional
    public WalletTopupRequest submitTopupRequest(UUID clientId, BigDecimal amount, WalletTopupRequest.PaymentMode paymentMode,
                                                  String paymentReference, UUID documentId, String idempotencyKey, String requestedBy) {
        if (paymentReference == null || paymentReference.isBlank()) {
            throw new BadRequestException("paymentReference is required");
        }
        String key = normalizeKey(idempotencyKey);
        repository.findByIdempotencyKey(key).ifPresent(existing -> {
            throw new BadRequestException("This top-up was already submitted (idempotency key reused)");
        });
        WalletTopupRequest request = new WalletTopupRequest();
        request.setClientId(clientId);
        request.setAmount(amount);
        request.setPaymentMode(paymentMode);
        request.setPaymentReference(paymentReference.trim());
        request.setDocumentId(documentId);
        request.setIdempotencyKey(key);
        request.setRequestedBy(requestedBy);
        request.setStatus(WalletTopupRequest.Status.PENDING);
        return repository.save(request);
    }

    @Transactional
    public WalletTopupRequest reviewTopupRequest(UUID requestId, boolean approve, AuthenticatedUser reviewer) {
        WalletTopupRequest request = repository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Top-up request not found"));
        if (request.getStatus() != WalletTopupRequest.Status.PENDING) {
            throw new BadRequestException("This top-up request has already been reviewed");
        }
        makerCheckerPolicy.assertCanReviewTopup(reviewer, request.getAmount());
        String reviewedBy = reviewer.phoneNumber();
        request.setReviewedBy(reviewedBy);
        if (!approve) {
            request.setStatus(WalletTopupRequest.Status.REJECTED);
            request = repository.save(request);
            auditService.record("TOPUP_REJECTED", "WalletTopupRequest", request.getId().toString(), "rejected by " + reviewedBy);
            return request;
        }
        request.setStatus(WalletTopupRequest.Status.COMPLETED);
        request = repository.save(request);
        walletService.credit(request.getClientId(), WalletAccount.WalletType.WALLET, request.getAmount(),
                LedgerEntry.ReferenceType.TOPUP, request.getId().toString(), "topup-credit-" + request.getId(),
                "Wallet top-up (" + request.getPaymentMode() + ") approved by " + reviewedBy);
        auditService.record("TOPUP_APPROVED", "WalletTopupRequest", request.getId().toString(),
                "approved by " + reviewedBy + ", credited " + request.getAmount());
        return request;
    }

    public List<WalletTopupRequest> listForClient(UUID clientId) {
        return repository.findByClientIdOrderByCreatedAtDesc(clientId);
    }

    public List<WalletTopupRequest> listPending() {
        return repository.findByStatusOrderByCreatedAtAsc(WalletTopupRequest.Status.PENDING);
    }

    private String normalizeKey(String idempotencyKey) {
        return (idempotencyKey == null || idempotencyKey.isBlank()) ? UUID.randomUUID().toString() : idempotencyKey;
    }
}
