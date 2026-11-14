package com.b2bfintech.wallet.service;

import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.wallet.entity.LedgerEntry;
import com.b2bfintech.wallet.entity.PlatformTransaction;
import com.b2bfintech.wallet.entity.VendorTransaction;
import com.b2bfintech.wallet.entity.WalletAccount;
import com.b2bfintech.wallet.repository.LedgerEntryRepository;
import com.b2bfintech.wallet.repository.PlatformTransactionRepository;
import com.b2bfintech.wallet.repository.VendorTransactionRepository;
import com.b2bfintech.wallet.repository.WalletAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The one place that ever changes a wallet balance. Every call is transactional, takes a
 * row-level lock on the wallet first (so concurrent submissions can't both pass an
 * insufficient-balance check), and writes an append-only {@link LedgerEntry} alongside the
 * balance update, plus the opposite row in {@link PlatformTransaction}. Callers stop repeats themselves, for example by checking the due or the
 * top-up request is still unpaid inside the same transaction.
 */
@Service
public class WalletService {

    private final WalletAccountRepository walletAccountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final WalletOwnerResolver ownerResolver;
    private final PlatformTransactionRepository platformTransactionRepository;
    private final VendorTransactionRepository vendorTransactionRepository;

    public WalletService(WalletAccountRepository walletAccountRepository, LedgerEntryRepository ledgerEntryRepository,
                         WalletOwnerResolver ownerResolver, PlatformTransactionRepository platformTransactionRepository,
                         VendorTransactionRepository vendorTransactionRepository) {
        this.walletAccountRepository = walletAccountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.ownerResolver = ownerResolver;
        this.platformTransactionRepository = platformTransactionRepository;
        this.vendorTransactionRepository = vendorTransactionRepository;
    }

    @Transactional
    public WalletAccount ensureAccount(String clientId, WalletAccount.WalletType walletType) {
        return walletAccountRepository.findByWalletUserId(clientId)
                .orElseGet(() -> {
                    WalletAccount account = new WalletAccount();
                    account.setWalletUserId(clientId);
                    account.setWalletUserType(ownerResolver.typeOf(clientId));
                    account.setBalance(BigDecimal.ZERO);
                    account.setActive(true);
                    return walletAccountRepository.save(account);
                });
    }

    private static void requireActive(WalletAccount account) {
        if (!account.isActive()) {
            throw new BadRequestException("This wallet is not active");
        }
    }

    public BigDecimal getBalance(String clientId, WalletAccount.WalletType walletType) {
        return ensureAccount(clientId, walletType).getBalance();
    }

    /** @throws BadRequestException if the wallet's available balance can't cover {@code amount}. */
    @Transactional
    public LedgerEntry debit(String clientId, BigDecimal amount, String category, String createdBy) {
        ensureAccount(clientId, WalletAccount.WalletType.WALLET);
        WalletAccount account = walletAccountRepository.findForUpdate(clientId).orElseThrow();
        requireActive(account);
        if (account.getBalance().compareTo(amount) < 0) {
            throw new BadRequestException("Insufficient wallet balance: available " +
                    account.getBalance() + ", required " + amount);
        }
        account.setBalance(account.getBalance().subtract(amount));
        walletAccountRepository.save(account);
        return writeEntry(account, LedgerEntry.EntryType.DEBIT, amount, category, createdBy);
    }

    @Transactional
    public LedgerEntry credit(String clientId, BigDecimal amount, String category, String createdBy) {
        ensureAccount(clientId, WalletAccount.WalletType.WALLET);
        WalletAccount account = walletAccountRepository.findForUpdate(clientId).orElseThrow();
        requireActive(account);
        account.setBalance(account.getBalance().add(amount));
        walletAccountRepository.save(account);
        return writeEntry(account, LedgerEntry.EntryType.CREDIT, amount, category, createdBy);
    }

    private LedgerEntry writeEntry(WalletAccount account, LedgerEntry.EntryType entryType, BigDecimal amount,
                                   String category, String createdBy) {
        LedgerEntry entry = new LedgerEntry();
        entry.setWalletAccount(account);
        entry.setEntryType(entryType);
        entry.setAmount(amount);
        entry.setCategory(category);
        entry.setCreatedBy(createdBy);
        entry = ledgerEntryRepository.save(entry);
        recordPlatform(account.getId(),
                entryType == LedgerEntry.EntryType.CREDIT
                        ? PlatformTransaction.Type.DEBIT : PlatformTransaction.Type.CREDIT,
                amount, category, createdBy);
        return entry;
    }

    /**
     * Money paid between Paytrix and a vendor outside the app. Paying the vendor for a bill is a
     * vendor CREDIT and a platform DEBIT. The vendor paying its commission is a vendor DEBIT and a
     * platform CREDIT.
     */
    @Transactional
    public VendorTransaction recordVendorTransaction(String vendorId, VendorTransaction.Category category,
                                                     BigDecimal amount, String createdBy) {
        if (amount == null || amount.signum() <= 0) {
            throw new BadRequestException("The amount must be greater than zero");
        }
        WalletAccount vendorWallet = ensureAccount(vendorId, WalletAccount.WalletType.WALLET);
        PlatformTransaction.Type vendorSide = category == VendorTransaction.Category.BILL_PAID
                ? PlatformTransaction.Type.CREDIT : PlatformTransaction.Type.DEBIT;
        VendorTransaction row = new VendorTransaction();
        row.setVendorId(vendorId);
        row.setType(vendorSide);
        row.setAmount(amount);
        row.setCategory(category);
        row.setCreatedBy(createdBy);
        row = vendorTransactionRepository.save(row);
        recordPlatform(vendorWallet.getId(),
                vendorSide == PlatformTransaction.Type.CREDIT
                        ? PlatformTransaction.Type.DEBIT : PlatformTransaction.Type.CREDIT,
                amount, category.name(), createdBy);
        return row;
    }

    private void recordPlatform(UUID walletId, PlatformTransaction.Type type, BigDecimal amount, String category, String createdBy) {
        PlatformTransaction row = new PlatformTransaction();
        row.setWalletId(walletId);
        row.setType(type);
        row.setAmount(amount);
        row.setCategory(category);
        row.setCreatedBy(createdBy);
        platformTransactionRepository.save(row);
    }
}
