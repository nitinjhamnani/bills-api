package com.b2bfintech.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One tracked account. The latest provider response, when the provider returned HTTP 200, is
 * {@link #latestFetch}. Screens still read amount, dates, and status from that fetch.
 */
@Entity
@Table(name = "bills")
@Getter
@Setter
@NoArgsConstructor
public class Bill {

    @Id
    @Column(name = "bill_id", nullable = false, length = 10)
    private String billId;

    @Column(name = "operator_name", nullable = false, length = 300)
    private String operatorName;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 16)
    private SourceType sourceType = SourceType.CORP;

    @Column(name = "source_id", nullable = false, length = 8)
    private String sourceId;

    @Column(name = "branch_id", nullable = false, length = 8)
    private String branchId;

    @Column(name = "user_id", nullable = false, length = 6)
    private String userId;

    @Column(name = "category_name", nullable = false, length = 200)
    private String categoryName;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "primary_param_name", nullable = false, length = 200)
    private String primaryParamName;

    @Column(name = "primary_param_value", nullable = false, length = 100)
    private String primaryParamValue;

    @Column(name = "input_param_json", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "CAST(? AS jsonb)")
    private String inputParamJson;

    /** Sent as UserMobileNumber on every fetch. Not one of the biller's parameters. */
    @Column(name = "customer_mobile", length = 10)
    private String customerMobile;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "latest_bill_fetch_id")
    private BillFetch latestFetch;

    /** Provider operator reference used for the next fetch. It is not a stored column. */
    @Transient
    private String operatorId;

    public String getId() {
        return billId;
    }

    public String getClientId() {
        return sourceId;
    }

    public String getBillerName() {
        return operatorName;
    }

    public String getBillerId() {
        return operatorId;
    }

    public String getCustomerBillAccountNumber() {
        return primaryParamValue;
    }

    public String getBillServiceCode() {
        return categoryName;
    }

    public String getProviderCategoryId() {
        return categoryName;
    }

    public String getProviderBillerId() {
        return operatorId;
    }

    public String getProviderCode() {
        return latestFetch == null ? "BRANCHX" : latestFetch.getFetchProvider();
    }

    public String getFetchParameters() {
        return inputParamJson;
    }

    public String getCustomerName() {
        return latestFetch == null ? null : latestFetch.getBillConsumerName();
    }

    public String getCustomerMobileNumber() {
        return customerMobile;
    }

    public BigDecimal getAmount() {
        return latestFetch == null ? null : latestFetch.getBillAmount();
    }

    public String getAmountBreakdown() {
        return null;
    }

    public String getBillNumber() {
        return null;
    }

    public String getBillDate() {
        return latestFetch == null || latestFetch.getBillGeneratedDate() == null
                ? null : latestFetch.getBillGeneratedDate().toString();
    }

    public String getDueDate() {
        return latestFetch == null || latestFetch.getBillDueDate() == null
                ? null : latestFetch.getBillDueDate().toString();
    }

    public String getBillPeriod() {
        return latestFetch == null ? null : latestFetch.getBillCycle();
    }

    public PaymentStatus getPaymentStatus() {
        if (latestFetch == null) {
            return PaymentStatus.PROCESSING;
        }
        if (latestFetch.getFetchStatus() == BillFetch.FetchStatus.FAILED) {
            return PaymentStatus.FAILED;
        }
        if (latestFetch.getBillPaymentStatus() == BillFetch.PaymentStatus.PAID) {
            return PaymentStatus.PAID;
        }
        if (latestFetch.getBillStatus() == BillFetch.BillStatus.DUE) {
            return PaymentStatus.PENDING;
        }
        return PaymentStatus.PROCESSING;
    }

    public BillStatus getBillStatus() {
        if (latestFetch == null) {
            return BillStatus.PROCESSING;
        }
        return switch (latestFetch.getBillStatus()) {
            case DUE -> BillStatus.DUE;
            case PAID -> BillStatus.PAID;
            case FAILED -> BillStatus.FAILED;
            case PENDING -> BillStatus.PROCESSING;
        };
    }

    public Instant getLastFetchAt() {
        return latestFetch == null ? null : latestFetch.getFetchedOn();
    }

    public boolean isLastFetchFailed() {
        return latestFetch != null && latestFetch.getFetchStatus() == BillFetch.FetchStatus.FAILED;
    }

    public String getLastFetchFailureReason() {
        return isLastFetchFailed() ? latestFetch.getFetchRemarks() : null;
    }

    public String getLastFetchResponseSnapshot() {
        return latestFetch == null ? null : latestFetch.getFetchSnapshotJson();
    }

    public Instant getCreatedAt() {
        return getLastFetchAt();
    }

    public Instant getUpdatedAt() {
        return getLastFetchAt();
    }

    public boolean isAutoFetchEnabled() {
        return true;
    }

    public boolean isAutoPayEnabled() {
        return false;
    }

    public String getLastPaidDueDate() {
        return null;
    }

    public enum SourceType {
        CORP, VENDOR
    }

    /** Status the existing payment cycle still speaks. A bill with no stored fetch is PROCESSING. */
    public enum PaymentStatus {
        PROCESSING, PENDING, PAID, FAILED
    }

    /** Status the existing payment cycle still speaks. */
    public enum BillStatus {
        PROCESSING, DUE, PAID, FAILED
    }
}
