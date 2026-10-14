package com.b2bfintech.wallet.repository;

import com.b2bfintech.wallet.entity.WalletTopupRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletTopupRequestRepository extends JpaRepository<WalletTopupRequest, UUID> {
    List<WalletTopupRequest> findByClientIdOrderByCreatedAtDesc(UUID clientId);
    List<WalletTopupRequest> findByStatusOrderByCreatedAtAsc(WalletTopupRequest.Status status);
    Optional<WalletTopupRequest> findByIdempotencyKey(String idempotencyKey);

    /** Report query: every top-up request (any status, any client) created within a window. */
    List<WalletTopupRequest> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant from, Instant to);
    List<WalletTopupRequest> findByClientIdAndCreatedAtBetweenOrderByCreatedAtDesc(UUID clientId, Instant from, Instant to);
}
