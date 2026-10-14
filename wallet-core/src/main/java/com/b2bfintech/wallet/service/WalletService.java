package com.b2bfintech.wallet.service;

import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.wallet.entity.LedgerEntry;
import com.b2bfintech.wallet.entity.WalletAccount;
import com.b2bfintech.wallet.repository.LedgerEntryRepository;
import com.b2bfintech.wallet.repository.WalletAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The one place that ever changes a wallet balance. Every call is transactional, takes a
 * row-level lock on the wallet account first (so concurrent submissions can't both pass an
 * insufficient-balance check), and writes an append-only {@link LedgerEntry} alongside the
 * balance update - see implementation plan &sect;6.
 *
 * <p>Callers pass an {@code idempotencyKey} for anything that could be retried (bill submission,
 * top-up, redemption); a duplicate key is rejected before any balance change happens.
 */
@Service
public class WalletService {

    private final WalletAccountRepository walletAccountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public WalletService(WalletAccountRepository walletAccountRepository, LedgerEntryRepository ledgerEntryRepository) {
        this.walletAccountRepository = walletAccountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional
    public WalletAccount ensureAccount(UUID clientId, WalletAccount.WalletType walletType) {
        return walletAccountRepository.findByClientIdAndWalletType(clientId, walletType)
                .orElseGet(() -> {
                    WalletAccount account = new WalletAccount();
                    account.setClientId(clientId);
                    account.setWalletType(walletType);
                    account.setBalance(BigDecimal.ZERO);
                    return walletAccountRepository.save(account);
                });
    }

    public BigDecimal getBalance(UUID clientId, WalletAccount.WalletType walletType) {
        return ensureAccount(clientId, walletType).getBalance();
    }

    /** @throws BadRequestException if the wallet's available balance can't cover {@code amount}. */
    @Transactional
    public LedgerEntry debit(UUID clientId, WalletAccount.WalletType walletType, BigDecimal amount,
                              LedgerEntry.ReferenceType referenceType, String referenceId, String idempotencyKey, String description) {
        if (idempotencyKey != null && ledgerEntryRepository.existsByIdempotencyKey(idempotencyKey)) {
            throw new BadRequestException("This request was already processed (idempotency key reused)");
        }
        ensureAccount(clientId, walletType);
        WalletAccount account = walletAccountRepository.findForUpdate(clientId, walletType).orElseThrow();
        if (account.getBalance().compareTo(amount) < 0) {
            throw new BadRequestException("Insufficient " + walletType + " balance: available " +
                    account.getBalance() + ", required " + amount);
        }
        account.setBalance(account.getBalance().subtract(amount));
        walletAccountRepository.save(account);
        return writeEntry(account, LedgerEntry.EntryType.DEBIT, amount, referenceType, referenceId, idempotencyKey, description);
    }

    @Transactional
    public LedgerEntry credit(UUID clientId, WalletAccount.WalletType walletType, BigDecimal amount,
                               LedgerEntry.ReferenceType referenceType, String referenceId, String idempotencyKey, String description) {
        if (idempotencyKey != null && ledgerEntryRepository.existsByIdempotencyKey(idempotencyKey)) {
            throw new BadRequestException("This request was already processed (idempotency key reused)");
        }
        ensureAccount(clientId, walletType);
        WalletAccount account = walletAccountRepository.findForUpdate(clientId, walletType).orElseThrow();
        account.setBalance(account.getBalance().add(amount));
        walletAccountRepository.save(account);
        return writeEntry(account, LedgerEntry.EntryType.CREDIT, amount, referenceType, referenceId, idempotencyKey, description);
    }

    private LedgerEntry writeEntry(WalletAccount account, LedgerEntry.EntryType entryType, BigDecimal amount,
                                    LedgerEntry.ReferenceType referenceType, String referenceId, String idempotencyKey, String description) {
        LedgerEntry entry = new LedgerEntry();
        entry.setWalletAccount(account);
        entry.setEntryType(entryType);
        entry.setAmount(amount);
        entry.setBalanceAfter(account.getBalance());
        entry.setReferenceType(referenceType);
        entry.setReferenceId(referenceId);
        entry.setIdempotencyKey(idempotencyKey);
        entry.setDescription(description);
        return ledgerEntryRepository.save(entry);
    }
}
