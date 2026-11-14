package com.b2bfintech.wallet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Money between Paytrix and a vendor, paid outside the app. CREDIT: Paytrix paid the vendor for a
 * settled bill. DEBIT: the vendor paid Paytrix its commission.
 */
@Entity
@Table(name = "vendor_transaction")
@Getter
@Setter
@NoArgsConstructor
public class VendorTransaction {

    @Id
    @GeneratedValue
    @Column(name = "transaction_id")
    private UUID id;

    @Column(name = "vendor_id", nullable = false, length = 8)
    private String vendorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 10)
    private PlatformTransaction.Type type;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 6, updatable = false)
    private String createdBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_category", nullable = false, length = 16)
    private Category category;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public enum Category {
        BILL_PAID, COMMISSION
    }
}
