package com.b2bfintech.organisation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Coverage for one category, provider, and amount range.
 * Vendors on the rule compete by commission. The highest live rate is assigned first.
 */
@Entity
@Table(name = "commission_rule")
@Getter
@Setter
@NoArgsConstructor
public class CommissionRule {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "category_id", nullable = false)
    private String categoryId;

    @Column(name = "category_name", nullable = false)
    private String categoryName;

    @Column(name = "provider_biller_id", nullable = false)
    private String providerBillerId;

    @Column(name = "provider_name", nullable = false)
    private String providerName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "range_id", nullable = false)
    private CommissionAmountRange range;

    /** An inactive rule assigns no bills. Vendor rates stay on file. */
    @Column(nullable = false)
    private boolean active = true;

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
}
