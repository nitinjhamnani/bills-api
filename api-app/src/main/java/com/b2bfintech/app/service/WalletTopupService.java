package com.b2bfintech.app.service;

import com.b2bfintech.common.audit.AuditService;
import com.b2bfintech.common.document.DocumentStorageService;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.wallet.entity.LedgerEntry;
import com.b2bfintech.wallet.entity.WalletTransactionRequest;
import com.b2bfintech.wallet.repository.WalletTransactionRequestRepository;
import com.b2bfintech.wallet.service.WalletService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A corporate admin asks for a wallet top-up with the UTR and proof of the transfer. Paytrix
 * approves it, which credits the wallet, or rejects it.
 */
@Service
public class WalletTopupService {

    private static final long MAX_PROOF_BYTES = 5L * 1024 * 1024;
    private static final Set<String> PROOF_TYPES = Set.of(
            "application/pdf", "image/png", "image/jpeg", "image/jpg", "image/webp");

    private final WalletTransactionRequestRepository repository;
    private final WalletService walletService;
    private final AuditService auditService;
    private final MakerCheckerPolicy makerCheckerPolicy;
    private final DocumentStorageService storageService;

    public WalletTopupService(WalletTransactionRequestRepository repository, WalletService walletService,
                              AuditService auditService, MakerCheckerPolicy makerCheckerPolicy,
                              DocumentStorageService storageService) {
        this.repository = repository;
        this.walletService = walletService;
        this.auditService = auditService;
        this.makerCheckerPolicy = makerCheckerPolicy;
        this.storageService = storageService;
    }

    @Transactional
    public WalletTransactionRequest submit(String corpId, BigDecimal amount, WalletTransactionRequest.Mode mode,
                                           String utr, MultipartFile proof, AuthenticatedUser requester) {
        if (amount == null || amount.signum() <= 0) {
            throw new BadRequestException("Enter an amount greater than zero");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new BadRequestException("The amount can have at most 2 decimal places");
        }
        if (mode == null) {
            throw new BadRequestException("Choose how you paid");
        }
        String trimmedUtr = utr == null ? "" : utr.trim();
        if (trimmedUtr.isEmpty()) {
            throw new BadRequestException("Enter the UTR or transaction id");
        }
        if (trimmedUtr.length() > 150) {
            throw new BadRequestException("The UTR must be 150 characters or less");
        }
        if (repository.existsByCorpIdAndUtrIgnoreCaseAndStatusNot(
                corpId, trimmedUtr, WalletTransactionRequest.Status.REJECTED)) {
            throw new BadRequestException("A top-up with this UTR has already been submitted");
        }
        requireProof(proof);
        WalletTransactionRequest request = new WalletTransactionRequest();
        request.setCorpId(corpId);
        request.setAmount(amount);
        request.setMode(mode);
        request.setUtr(trimmedUtr);
        request.setProof(storageService.store(proof, corpId));
        request.setCreatedBy(requester.displayName());
        request.setCreatedById(requester.userId());
        request.setStatus(WalletTransactionRequest.Status.PENDING);
        return repository.save(request);
    }

    @Transactional
    public WalletTransactionRequest review(UUID requestId, boolean approve, AuthenticatedUser reviewer) {
        WalletTransactionRequest request = repository.findForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Top-up request not found"));
        if (request.getStatus() != WalletTransactionRequest.Status.PENDING) {
            throw new BadRequestException("This top-up request has already been reviewed");
        }
        makerCheckerPolicy.assertCanReviewTopup(reviewer, request.getAmount());
        request.setApprovedBy(reviewer.displayName());
        request.setApprovedById(reviewer.userId());
        request.setApprovedOn(Instant.now());
        if (!approve) {
            request.setStatus(WalletTransactionRequest.Status.REJECTED);
            request = repository.save(request);
            auditService.record("TOPUP_REJECTED", "WalletTransactionRequest", request.getRequestId().toString(),
                    "rejected by " + reviewer.phoneNumber());
            return request;
        }
        request.setStatus(WalletTransactionRequest.Status.APPROVED);
        request = repository.save(request);
        walletService.credit(request.getCorpId(), request.getAmount(), LedgerEntry.RECHARGE, reviewer.userId());
        auditService.record("TOPUP_APPROVED", "WalletTransactionRequest", request.getRequestId().toString(),
                "approved by " + reviewer.phoneNumber() + ", credited " + request.getAmount());
        MoneyActionLog.record("wallet_approve", reviewer.userId(), request.getRequestId().toString());
        return request;
    }

    public WalletTransactionRequest get(UUID requestId) {
        return repository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Top-up request not found"));
    }

    public List<WalletTransactionRequest> listForClient(String corpId) {
        return repository.findByCorpIdOrderByCreatedDateDesc(corpId);
    }

    public List<WalletTransactionRequest> listPending() {
        return repository.findByStatusOrderByCreatedDateAsc(WalletTransactionRequest.Status.PENDING);
    }

    private static void requireProof(MultipartFile proof) {
        if (proof == null || proof.isEmpty()) {
            throw new BadRequestException("Attach the proof of the transfer");
        }
        if (proof.getSize() > MAX_PROOF_BYTES) {
            throw new BadRequestException("The proof must be 5 MB or smaller");
        }
        String type = proof.getContentType() == null ? "" : proof.getContentType().toLowerCase();
        if (!PROOF_TYPES.contains(type)) {
            throw new BadRequestException("The proof must be a PDF, PNG, JPG, or WEBP file");
        }
    }
}
