package com.b2bfintech.wallet.repository;

import com.b2bfintech.wallet.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    List<LedgerEntry> findByWalletAccountIdOrderByCreatedAtDesc(UUID walletAccountId);

    /** One wallet's transactions in a window, oldest first. The wallet is fetched for its owner id. */
    @Query("select e from LedgerEntry e join fetch e.walletAccount where e.walletAccount.id = :walletAccountId and e.createdAt between :from and :to order by e.createdAt asc")
    List<LedgerEntry> findByWalletAccountIdAndCreatedAtBetweenOrderByCreatedAtAsc(
            @Param("walletAccountId") UUID walletAccountId, @Param("from") Instant from, @Param("to") Instant to);

    /** Credits minus debits before a moment: the wallet's balance at that point. */
    @Query("select coalesce(sum(case when e.entryType = 'CREDIT' then e.amount else -e.amount end), 0) " +
            "from LedgerEntry e where e.walletAccount.id = :walletAccountId and e.createdAt < :before")
    BigDecimal balanceBefore(@Param("walletAccountId") UUID walletAccountId, @Param("before") Instant before);

    /**
     * Independently recomputes a wallet's balance by replaying its transactions (credits minus
     * debits). The reconciliation job compares it with the cached {@code wallet.balance}.
     */
    @Query("select coalesce(sum(case when e.entryType = 'CREDIT' then e.amount else -e.amount end), 0) " +
            "from LedgerEntry e where e.walletAccount.id = :walletAccountId")
    BigDecimal computeBalanceFromLedger(@Param("walletAccountId") UUID walletAccountId);
}
