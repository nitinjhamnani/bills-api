package com.b2bfintech.organisation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * A organisation-to-client convenience fee/markup, optionally scoped to one bill service
 * ({@code billServiceCode == null} means "applies to every service"). Applied on top of the
 * bill amount at submission time as a second wallet debit (implementation plan &sect;8.2).
 */
@Entity
@Table(name = "fee_config")
@Getter
@Setter
@NoArgsConstructor
public class FeeConfig {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "bill_service_code")
    private String billServiceCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_type", nullable = false)
    private FeeType feeType;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal value;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public BigDecimal computeFee(BigDecimal billAmount) {
        BigDecimal fee = feeType == FeeType.FLAT ? value : billAmount.multiply(value).movePointLeft(2);
        return fee.setScale(2, RoundingMode.HALF_UP);
    }

    public enum FeeType {
        FLAT, PERCENTAGE
    }
}
