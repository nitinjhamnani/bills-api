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
import org.hibernate.annotations.ColumnTransformer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One provider response for a bill. Only an HTTP 200 response is stored. */
@Entity
@Table(name = "bill_fetch")
@Getter
@Setter
@NoArgsConstructor
public class BillFetch {

    @Id
    @GeneratedValue
    @Column(name = "bill_fetch_id")
    private UUID billFetchId;

    @Column(name = "bill_id", nullable = false, length = 10)
    private String billId;

    @Column(name = "bill_amount", precision = 14, scale = 2)
    private BigDecimal billAmount;

    @Column(name = "bill_cycle", length = 40)
    private String billCycle;

    @Column(name = "bill_due_date")
    private LocalDate billDueDate;

    @Column(name = "bill_generated_date")
    private LocalDate billGeneratedDate;

    @Column(name = "bill_consumer_name", length = 200)
    private String billConsumerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "bill_status", nullable = false, length = 16)
    private BillStatus billStatus = BillStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "bill_payment_status", nullable = false, length = 16)
    private PaymentStatus billPaymentStatus = PaymentStatus.PENDING;

    @Column(name = "fetched_on", nullable = false)
    private Instant fetchedOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "fetch_status", nullable = false, length = 16)
    private FetchStatus fetchStatus = FetchStatus.SUCCESS;

    @Column(name = "fetch_remarks", length = 500)
    private String fetchRemarks;

    @Column(name = "fetch_provider", nullable = false, length = 32)
    private String fetchProvider;

    @Column(name = "fetch_snapshot_json", columnDefinition = "jsonb")
    @ColumnTransformer(write = "CAST(? AS jsonb)")
    private String fetchSnapshotJson;

    public enum BillStatus {
        DUE, PENDING, PAID, FAILED
    }

    public enum PaymentStatus {
        PENDING, PAID
    }

    public enum FetchStatus {
        SUCCESS, FAILED
    }
}
