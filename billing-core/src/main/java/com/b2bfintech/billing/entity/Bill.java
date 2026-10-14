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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A "Managed Bill" - one row per unique bill account (client + bill service + consumer number),
 * always reflecting the <b>latest</b> fetch cycle's data. This is what the Managed Bills screen
 * lists, and it also carries the auto-fetch/auto-pay configuration (replacing the old, separate
 * {@code CustomerBillAccount}).
 *
 * <p>Each fetch cycle that resolves to something payable spawns a {@link BillPayment} row instead
 * of a new {@code Bill} row - see {@link com.b2bfintech.billing.repository.BillPaymentRepository}.
 * Every event (created, fetched, paid, ...) is separately logged to {@link BillPaymentLog}.
 */
@Entity
@Table(name = "bill", uniqueConstraints =
        @UniqueConstraint(columnNames = {"client_id", "bill_service_code", "customer_bill_account_no"}))
@Getter
@Setter
@NoArgsConstructor
public class Bill {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "bill_service_code", nullable = false)
    private String billServiceCode;

    @Column(name = "biller_id")
    private String billerId;

    @Column(name = "biller_name")
    private String billerName;

    @Column(name = "customer_bill_account_no", nullable = false)
    private String customerBillAccountNumber;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "customer_mobile_number")
    private String customerMobileNumber;

    @Column(name = "provider_code")
    private String providerCode;

    @Column(name = "provider_category_id")
    private String providerCategoryId;

    @Column(name = "provider_location_id")
    private String providerLocationId;

    @Column(name = "provider_biller_id")
    private String providerBillerId;

    /** JSON object of {paramName: value} for the fields this biller declared. */
    @Column(name = "fetch_parameters")
    private String fetchParameters;

    @Column
    private BigDecimal amount;

    /** JSON array of {label, amount} line items from the latest fetch - plain text, same
     * convention as everywhere else in this schema (no JSONB-mapped JPA type set up). */
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

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.PROCESSING;

    @Enumerated(EnumType.STRING)
    @Column(name = "bill_status", nullable = false)
    private BillStatus billStatus = BillStatus.PROCESSING;

    /** Raw provider fetch response, as text - a full snapshot for audit/debugging. */
    @Column(name = "last_fetch_response_snapshot")
    private String lastFetchResponseSnapshot;

    @Column(name = "last_fetch_at")
    private Instant lastFetchAt;

    /** Drives the highlighted "fetch failed" badge on the Managed Bills screen. */
    @Column(name = "last_fetch_failed", nullable = false)
    private boolean lastFetchFailed = false;

    @Column(name = "last_fetch_failure_reason")
    private String lastFetchFailureReason;

    @Column(name = "auto_fetch_enabled", nullable = false)
    private boolean autoFetchEnabled = true;

    /** Client-level convenience only: if set, a fetch resolving to due auto-triggers the client's
     * own payment step (wallet debit) - the organisation must still manually pick up and pay.
     * Pays whatever amount is due, with no cap. */
    @Column(name = "auto_pay_enabled", nullable = false)
    private boolean autoPayEnabled = false;

    /** The due date/cycle identifier already paid for - guards against re-processing the same
     * fetched due amount on a later fetch tick (same role as the old CustomerBillAccount field). */
    @Column(name = "last_paid_due_date")
    private String lastPaidDueDate;

    @Column(name = "created_by")
    private String createdBy;

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

    /** PROCESSING: created but not yet fetched, or fetch transiently failed (retried next cycle).
     * PENDING: fetched, due amount &gt; 0, awaiting the client's payment action. PAID: either
     * fetched at zero due, or the client has since paid it. FAILED: fetch permanently failed. */
    public enum PaymentStatus {
        PROCESSING, PENDING, PAID, FAILED
    }

    /** Mirrors payment_status but from the biller-settlement point of view: DUE while pending or
     * while the organisation is still settling it with the biller; PAID once the organisation has
     * actually paid the biller. */
    public enum BillStatus {
        PROCESSING, DUE, PAID, FAILED
    }
}
