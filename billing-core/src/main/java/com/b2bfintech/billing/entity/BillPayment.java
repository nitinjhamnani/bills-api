package com.b2bfintech.billing.entity;

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

/**
 * One fetch/payment cycle for a {@link Bill} that resolved to something payable
 * (payment_status = PENDING at the time it was created). This is what the My Payments screens
 * (both client and organisation side) list, and it accumulates historically - a bill with many
 * paid cycles has many {@code BillPayment} rows, one per cycle, while {@link Bill} itself only
 * ever reflects the latest one.
 */
@Entity
@Table(name = "bill_payment")
@Getter
@Setter
@NoArgsConstructor
public class BillPayment {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "bill_id", nullable = false)
    private UUID billId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "amount_breakdown")
    private String amountBreakdown;

    @Column(name = "bill_number")
    private String billNumber;

    @Column(name = "bill_date")
    private String billDate;

    @Column(name = "due_date")
    private String dueDate;

    @Column(name = "bill_period")
    private String billPeriod;

    @Column(name = "api_response_snapshot")
    private String apiResponseSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "org_status", nullable = false)
    private OrgStatus orgStatus = OrgStatus.NA;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private Bill.PaymentStatus paymentStatus = Bill.PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "bill_status", nullable = false)
    private Bill.BillStatus billStatus = Bill.BillStatus.DUE;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "client_paid_at")
    private Instant clientPaidAt;

    @Column(name = "client_paid_by")
    private String clientPaidBy;

    @Column(name = "org_picked_up_at")
    private Instant orgPickedUpAt;

    @Column(name = "org_picked_up_by")
    private String orgPickedUpBy;

    @Column(name = "org_paid_at")
    private Instant orgPaidAt;

    @Column(name = "org_paid_by")
    private String orgPaidBy;

    @Column(name = "bbps_transaction_id")
    private String bbpsTransactionId;

    @Column(name = "external_transaction_id")
    private String externalTransactionId;

    /** How the organisation actually settled it with the biller (e.g. "BBPS", "NEFT", "MANUAL") -
     * free text captured on org mark-paid, for record-keeping only. */
    @Column(name = "bill_type")
    private String billType;

    @Column(name = "bill_pay_failure_reason")
    private String billPayFailureReason;

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

    /** NA: not yet paid by the client, so nothing for the organisation to do. PENDING: the client
     * has paid (wallet debited) - now with the organisation to settle with the biller. IN_PROCESS:
     * an organisation user has picked it up. PAID: the organisation has paid the biller. */
    public enum OrgStatus {
        NA, PENDING, IN_PROCESS, PAID
    }
}
