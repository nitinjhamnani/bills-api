package com.b2bfintech.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** Paytrix paid the vendor the full bill amount. Commission is collected later, not netted here. */
@Entity
@Table(name = "vendor_reimbursement")
@Getter
@Setter
@NoArgsConstructor
public class VendorReimbursement {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "vendor_client_id", nullable = false)
    private UUID vendorClientId;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 120)
    private String reference;

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;

    @Column(nullable = false, length = 50)
    private String actor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
