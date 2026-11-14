package com.b2bfintech.wallet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One append-only row per wallet movement. A wallet's balance always equals its credits minus its
 * debits. {@code category} is the bill's category in capitals, or {@link #RECHARGE} for money added.
 */
@Entity
@Table(name = "wallet_transactions")
@Getter
@Setter
@NoArgsConstructor
public class LedgerEntry {

    public static final String RECHARGE = "RECHARGE";

    @Id
    @GeneratedValue
    @Column(name = "transaction_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private WalletAccount walletAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 10)
    private EntryType entryType;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 6, updatable = false)
    private String createdBy;

    @Column(name = "transaction_category", nullable = false, length = 40)
    private String category;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public BigDecimal signedAmount() {
        return entryType == EntryType.CREDIT ? amount : amount.negate();
    }

    /** Text for the ledger screens, since no description is stored. */
    public String describe() {
        String label = category == null ? "" : category.charAt(0) + category.substring(1).toLowerCase().replace('_', ' ');
        if (RECHARGE.equals(category)) {
            return entryType == EntryType.CREDIT ? "Wallet recharge" : "Recharge reversal";
        }
        return entryType == EntryType.DEBIT ? label + " bill payment" : label + " refund";
    }

    /** Turns a bill category name such as "Electricity" into the stored form, "ELECTRICITY". */
    public static String categoryOf(String billCategoryName) {
        if (billCategoryName == null || billCategoryName.isBlank()) {
            throw new IllegalArgumentException("A bill category is required for a wallet transaction");
        }
        return billCategoryName.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    public enum EntryType {
        DEBIT, CREDIT
    }
}
