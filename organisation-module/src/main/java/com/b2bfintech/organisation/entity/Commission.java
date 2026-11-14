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
import java.time.Instant;
import java.util.UUID;

/** A vendor's commission for one operator and amount range. Only approved, active rows assign bills. */
@Entity
@Table(name = "commissions")
@Getter
@Setter
@NoArgsConstructor
public class Commission {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "min_range", nullable = false, precision = 14, scale = 2)
    private BigDecimal minRange;

    @Column(name = "max_range", precision = 14, scale = 2)
    private BigDecimal maxRange;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "operator_id", nullable = false, length = 80)
    private String operatorId;

    @Column(name = "operator_name", nullable = false, length = 300)
    private String operatorName;

    @Column(name = "vendor_id", nullable = false, length = 8)
    private String vendorId;

    @Column(name = "vendor_name", nullable = false, length = 200)
    private String vendorName;

    @Column(name = "commission", nullable = false, precision = 6, scale = 3)
    private BigDecimal commission;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.APPROVED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by_id", length = 6, updatable = false)
    private String createdById;

    @Column(name = "updated_by_id", length = 6)
    private String updatedById;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

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

    public boolean covers(BigDecimal amount) {
        return amount != null && amount.compareTo(minRange) >= 0 && (maxRange == null || amount.compareTo(maxRange) <= 0);
    }

    public boolean isLive() {
        return active && status == Status.APPROVED;
    }

    public enum Status {
        APPROVED, DRAFT
    }
}
