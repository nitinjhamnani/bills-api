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

import java.time.Instant;
import java.util.UUID;

/** A vendor who sent an assigned bill back. That vendor is not offered the same payment again. */
@Entity
@Table(name = "bill_payment_rejection")
@Getter
@Setter
@NoArgsConstructor
public class BillPaymentRejection {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "bill_payment_id", nullable = false)
    private UUID billPaymentId;

    @Column(name = "vendor_client_id", nullable = false)
    private UUID vendorClientId;

    @Column(nullable = false, length = 300)
    private String reason;

    @Column(name = "rejected_by", nullable = false, length = 50)
    private String rejectedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
