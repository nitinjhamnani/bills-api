package com.b2bfintech.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Made when the checker verifies a bill a vendor paid. Paytrix then pays the vendor and collects the commission. */
@Entity
@Table(name = "vendor_payments")
@Getter
@Setter
@NoArgsConstructor
public class VendorPayment {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "bill_payment_id", nullable = false, unique = true)
    private UUID billPaymentId;

    @Column(name = "bill_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal billAmount;

    @Column(name = "bill_paid_on", nullable = false)
    private Instant billPaidOn;

    @Column(name = "commission_percent", precision = 6, scale = 3)
    private BigDecimal commissionPercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "commission_status", nullable = false, length = 16)
    private CommissionStatus commissionStatus = CommissionStatus.PENDING;

    @Column(name = "commission_reference_id", length = 120)
    private String commissionReferenceId;

    @Column(name = "commission_mode", length = 40)
    private String commissionMode;

    @Column(name = "commission_received", nullable = false)
    private boolean commissionReceived;

    @Column(name = "payment_mode", length = 40)
    private String paymentMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 16)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(name = "payment_made_on")
    private Instant paymentMadeOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_made_by", length = 16)
    private PaymentMadeBy paymentMadeBy;

    @Column(name = "payment_made_by_id", length = 6)
    private String paymentMadeById;

    @Column(name = "payment_reference_id", length = 120)
    private String paymentReferenceId;

    public enum CommissionStatus {
        PENDING, RECEIVED
    }

    public enum PaymentStatus {
        PENDING, PAID
    }

    public enum PaymentMadeBy {
        ADMIN, CHECKER, MAKER
    }

    /** The commission due back from the vendor, or zero when no rate was found. */
    public BigDecimal commissionAmount() {
        if (commissionPercent == null || billAmount == null) {
            return BigDecimal.ZERO;
        }
        return billAmount.multiply(commissionPercent).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
    }
}
