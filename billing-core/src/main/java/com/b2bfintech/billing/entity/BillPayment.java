package com.b2bfintech.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Made when a corporate pays one due fetch. The bill is then with Paytrix or with a vendor until it
 * is paid to the biller and verified.
 */
@Entity
@Table(name = "bill_payment")
@Getter
@Setter
@NoArgsConstructor
public class BillPayment {

    public static final String PAYTRIX_ID = "PAYTRIX";
    public static final String PAYTRIX_NAME = "Paytrix";

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "bill_fetch_id", nullable = false)
    private BillFetch billFetch;

    @Column(name = "bill_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal billAmount;

    @Column(name = "corp_payment_on", nullable = false)
    private Instant corpPaymentOn;

    @Column(name = "corp_id", nullable = false, length = 8)
    private String corpId;

    @Column(name = "corp_name", nullable = false, length = 200)
    private String corpName;

    @Column(name = "corp_paid_user_id", length = 6)
    private String corpPaidUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "corp_payment_status", nullable = false, length = 16)
    private CorpPaymentStatus corpPaymentStatus = CorpPaymentStatus.PAID;

    @Enumerated(EnumType.STRING)
    @Column(name = "bill_assigned_to", nullable = false, length = 16)
    private AssignedTo billAssignedTo = AssignedTo.ORG;

    @Column(name = "bill_assigned_to_id", nullable = false, length = 8)
    private String billAssignedToId = PAYTRIX_ID;

    @Column(name = "bill_assigned_to_name", nullable = false, length = 200)
    private String billAssignedToName = PAYTRIX_NAME;

    @Enumerated(EnumType.STRING)
    @Column(name = "bill_assigned_to_status", nullable = false, length = 16)
    private AssignedStatus billAssignedToStatus = AssignedStatus.PENDING;

    @Column(name = "bill_assigned_on", nullable = false)
    private Instant billAssignedOn;

    @Column(name = "bill_paid_on")
    private Instant billPaidOn;

    @Column(name = "transaction_reference_number", length = 120)
    private String transactionReferenceNumber;

    @Column(name = "bbps_external_reference", length = 120)
    private String bbpsExternalReference;

    @Column(name = "payment_proof", length = 500)
    private String paymentProof;

    @Column(name = "verified_by", length = 150)
    private String verifiedBy;

    @Column(name = "verified_by_id", length = 6)
    private String verifiedById;

    @Column(name = "verified_on")
    private Instant verifiedOn;

    @Column(name = "is_verified", nullable = false)
    private boolean verified;

    public void assignToPaytrix(Instant when) {
        billAssignedTo = AssignedTo.ORG;
        billAssignedToId = PAYTRIX_ID;
        billAssignedToName = PAYTRIX_NAME;
        billAssignedToStatus = AssignedStatus.PENDING;
        billAssignedOn = when;
    }

    public void assignToVendor(String vendorId, String vendorName, Instant when) {
        billAssignedTo = AssignedTo.VENDOR;
        billAssignedToId = vendorId;
        billAssignedToName = vendorName;
        billAssignedToStatus = AssignedStatus.PENDING;
        billAssignedOn = when;
    }

    public boolean isWithVendor() {
        return billAssignedTo == AssignedTo.VENDOR;
    }

    public String getBillId() {
        return billFetch == null ? null : billFetch.getBillId();
    }

    public String getClientId() {
        return corpId;
    }

    public BigDecimal getAmount() {
        return billAmount;
    }

    public String getAssignedVendorClientId() {
        return isWithVendor() ? billAssignedToId : null;
    }

    public String getDueDate() {
        return billFetch == null || billFetch.getBillDueDate() == null ? null : billFetch.getBillDueDate().toString();
    }

    public String getBillDate() {
        return billFetch == null || billFetch.getBillGeneratedDate() == null
                ? null : billFetch.getBillGeneratedDate().toString();
    }

    public String getBillPeriod() {
        return billFetch == null ? null : billFetch.getBillCycle();
    }

    public String getApiResponseSnapshot() {
        return billFetch == null ? null : billFetch.getFetchSnapshotJson();
    }

    /** The settlement step the Paytrix and vendor screens show. */
    public OrgStatus getOrgStatus() {
        if (isWithVendor()) {
            if (verified) {
                return OrgStatus.PAID;
            }
            return billPaidOn != null ? OrgStatus.IN_PROCESS : OrgStatus.PENDING;
        }
        if (billPaidOn != null) {
            return OrgStatus.PAID;
        }
        return billAssignedToStatus == AssignedStatus.IN_PROGRESS ? OrgStatus.IN_PROCESS : OrgStatus.PENDING;
    }

    /** Where a vendor-held bill is. Null while Paytrix holds it. */
    public VendorStage getVendorStage() {
        if (!isWithVendor()) {
            return null;
        }
        if (verified) {
            return VendorStage.VERIFIED;
        }
        if (billPaidOn != null) {
            return VendorStage.VENDOR_PAID;
        }
        return billAssignedToStatus == AssignedStatus.IN_PROGRESS ? VendorStage.IN_PROGRESS : VendorStage.ASSIGNED;
    }

    public Bill.PaymentStatus getPaymentStatus() {
        return Bill.PaymentStatus.PAID;
    }

    public Bill.BillStatus getBillStatus() {
        return getOrgStatus() == OrgStatus.PAID ? Bill.BillStatus.PAID : Bill.BillStatus.DUE;
    }

    public Instant getClientPaidAt() {
        return corpPaymentOn;
    }

    public String getClientPaidBy() {
        return corpPaidUserId;
    }

    public Instant getOrgPaidAt() {
        return isWithVendor() ? verifiedOn : billPaidOn;
    }

    public Instant getAssignedAt() {
        return isWithVendor() ? billAssignedOn : null;
    }

    public Instant getVendorMarkedPaidAt() {
        return isWithVendor() ? billPaidOn : null;
    }

    public String getVendorPaymentReference() {
        return isWithVendor() ? transactionReferenceNumber : null;
    }

    public String getExternalTransactionId() {
        return isWithVendor() ? null : transactionReferenceNumber;
    }

    public String getBbpsTransactionId() {
        return bbpsExternalReference;
    }

    public Instant getBbpsVerifiedAt() {
        return verifiedOn;
    }

    public Instant getCreatedAt() {
        return corpPaymentOn;
    }

    public Instant getUpdatedAt() {
        Instant latest = corpPaymentOn;
        for (Instant candidate : new Instant[]{billAssignedOn, billPaidOn, verifiedOn}) {
            if (candidate != null && (latest == null || candidate.isAfter(latest))) {
                latest = candidate;
            }
        }
        return latest;
    }

    public enum CorpPaymentStatus {
        PAID
    }

    public enum AssignedTo {
        ORG, VENDOR
    }

    public enum AssignedStatus {
        PENDING, IN_PROGRESS
    }

    /** PENDING: with Paytrix or the vendor and not yet paid to the biller. IN_PROCESS: Paytrix
     * picked it up, or the vendor marked it paid. PAID: Paytrix paid the biller, or the vendor's
     * payment was verified. */
    public enum OrgStatus {
        PENDING, IN_PROCESS, PAID
    }

    public enum VendorStage {
        ASSIGNED, IN_PROGRESS, VENDOR_PAID, VERIFIED
    }
}
