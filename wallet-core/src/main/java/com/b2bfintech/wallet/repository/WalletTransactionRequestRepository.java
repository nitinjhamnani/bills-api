package com.b2bfintech.wallet.repository;

import com.b2bfintech.wallet.entity.WalletTransactionRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletTransactionRequestRepository extends JpaRepository<WalletTransactionRequest, UUID> {

    /** Locks the request so two reviewers cannot both approve it and credit the wallet twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from WalletTransactionRequest r where r.requestId = :requestId")
    Optional<WalletTransactionRequest> findForUpdate(@Param("requestId") UUID requestId);

    List<WalletTransactionRequest> findByCorpIdOrderByCreatedDateDesc(String corpId);

    List<WalletTransactionRequest> findByStatusOrderByCreatedDateAsc(WalletTransactionRequest.Status status);

    boolean existsByCorpIdAndUtrIgnoreCaseAndStatusNot(String corpId, String utr, WalletTransactionRequest.Status status);

    List<WalletTransactionRequest> findByCreatedDateBetweenOrderByCreatedDateDesc(Instant from, Instant to);

    List<WalletTransactionRequest> findByCorpIdAndCreatedDateBetweenOrderByCreatedDateDesc(String corpId, Instant from, Instant to);
}
