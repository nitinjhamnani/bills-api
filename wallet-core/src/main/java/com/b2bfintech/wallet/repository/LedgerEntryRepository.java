package com.b2bfintech.wallet.repository;

import com.b2bfintech.wallet.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    List<LedgerEntry> findByWalletAccountIdOrderByCreatedAtDesc(UUID walletAccountId);
    boolean existsByIdempotencyKey(String idempotencyKey);

    /** Report queries: one client's wallet statement for a window, plus its opening balance
     * (the balance snapshot on the last entry strictly before the window starts). The wallet
     * account is fetched eagerly since {@code BillDtos.LedgerEntryDto} reads its wallet type. */
    @Query("select e from LedgerEntry e join fetch e.walletAccount where e.walletAccount.id = :walletAccountId and e.createdAt between :from and :to order by e.createdAt asc")
    List<LedgerEntry> findByWalletAccountIdAndCreatedAtBetweenOrderByCreatedAtAsc(UUID walletAccountId, Instant from, Instant to);
    Optional<LedgerEntry> findTopByWalletAccountIdAndCreatedAtBeforeOrderByCreatedAtDesc(UUID walletAccountId, Instant before);

    /** Every FEE-tagged ledger entry (organisation's convenience-fee revenue) in a window, across
     * every client's wallet - the wallet account is fetched eagerly since callers need its clientId. */
    @Query("select e from LedgerEntry e join fetch e.walletAccount where e.referenceType = :type and e.createdAt between :from and :to order by e.createdAt desc")
    List<LedgerEntry> findByReferenceTypeAndCreatedAtBetween(LedgerEntry.ReferenceType type, Instant from, Instant to);

    /** Every ledger movement tied to one bill (its debit, any fee, any refund) - for the bill details page. */
    @Query("select e from LedgerEntry e join fetch e.walletAccount where e.referenceId = :referenceId order by e.createdAt desc")
    List<LedgerEntry> findByReferenceIdOrderByCreatedAtDesc(String referenceId);

    /**
     * Independently recomputes a wallet's balance by replaying its ledger (credits minus
     * debits) - used by the reconciliation job to catch any drift between the cached
     * {@code wallet_account.balance} and its own audit trail.
     */
    @Query("select coalesce(sum(case when e.entryType = 'CREDIT' then e.amount else -e.amount end), 0) " +
            "from LedgerEntry e where e.walletAccount.id = :walletAccountId")
    BigDecimal computeBalanceFromLedger(UUID walletAccountId);
}
