package com.b2bfintech.organisation.entity;

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

/** One inclusive amount band. Bands cannot overlap. A null maximum means no upper limit. */
@Entity
@Table(name = "commission_amount_range")
@Getter
@Setter
@NoArgsConstructor
public class CommissionAmountRange {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "min_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal minAmount;

    @Column(name = "max_amount", precision = 14, scale = 2)
    private BigDecimal maxAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public boolean contains(BigDecimal amount) {
        if (amount == null || amount.compareTo(minAmount) < 0) {
            return false;
        }
        return maxAmount == null || amount.compareTo(maxAmount) <= 0;
    }
}
