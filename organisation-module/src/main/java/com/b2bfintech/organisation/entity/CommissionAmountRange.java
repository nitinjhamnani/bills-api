package com.b2bfintech.organisation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/** A preset amount range. Active ranges fill min and max in the commission form. Both ends are inclusive. */
@Entity
@Table(name = "range")
@Getter
@Setter
@NoArgsConstructor
public class CommissionAmountRange {

    @Id
    @GeneratedValue
    @Column(name = "range_id")
    private UUID id;

    @Column(name = "min", nullable = false, precision = 14, scale = 2)
    private BigDecimal minAmount;

    @Column(name = "max", precision = 14, scale = 2)
    private BigDecimal maxAmount;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public boolean contains(BigDecimal amount) {
        if (amount == null || amount.compareTo(minAmount) < 0) {
            return false;
        }
        return maxAmount == null || amount.compareTo(maxAmount) <= 0;
    }
}
