package com.b2bfintech.wallet.repository;

import com.b2bfintech.wallet.entity.PlatformTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlatformTransactionRepository extends JpaRepository<PlatformTransaction, UUID> {
    List<PlatformTransaction> findByWalletIdOrderByCreatedAtDesc(UUID walletId);

    List<PlatformTransaction> findAllByOrderByCreatedAtDesc();
}
