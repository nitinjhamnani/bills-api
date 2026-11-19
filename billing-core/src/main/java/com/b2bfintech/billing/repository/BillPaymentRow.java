package com.b2bfintech.billing.repository;

import com.b2bfintech.billing.entity.BillPayment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Pure projection of the {@code bill_payment} table without joining any other table. */
public record BillPaymentRow(
        UUID id,
        UUID billFetchId,
        BigDecimal billAmount,
        Instant corpPaymentOn,
        String corpId,
        String corpName,
        String corpPaidUserId,
        BillPayment.CorpPaymentStatus corpPaymentStatus,
        BillPayment.AssignedTo billAssignedTo,
        String billAssignedToId,
        String billAssignedToName,
        BillPayment.AssignedStatus billAssignedToStatus,
        Instant billAssignedOn,
        Instant billPaidOn,
        String transactionReferenceNumber,
        String bbpsExternalReference,
        String paymentProof,
        String verifiedBy,
        String verifiedById,
        Instant verifiedOn,
        boolean verified) {

    public boolean isWithVendor() {
        return billAssignedTo == BillPayment.AssignedTo.VENDOR;
    }

    public BillPayment.OrgStatus orgStatus() {
        if (isWithVendor()) {
            if (verified) {
                return BillPayment.OrgStatus.PAID;
            }
            return billPaidOn != null ? BillPayment.OrgStatus.IN_PROCESS : BillPayment.OrgStatus.PENDING;
        }
        if (billPaidOn != null) {
            return BillPayment.OrgStatus.PAID;
        }
        return billAssignedToStatus == BillPayment.AssignedStatus.IN_PROGRESS
                ? BillPayment.OrgStatus.IN_PROCESS
                : BillPayment.OrgStatus.PENDING;
    }

    public BillPayment.VendorStage vendorStage() {
        if (!isWithVendor()) {
            return null;
        }
        if (verified) {
            return BillPayment.VendorStage.VERIFIED;
        }
        if (billPaidOn != null) {
            return BillPayment.VendorStage.VENDOR_PAID;
        }
        return billAssignedToStatus == BillPayment.AssignedStatus.IN_PROGRESS
                ? BillPayment.VendorStage.IN_PROGRESS
                : BillPayment.VendorStage.ASSIGNED;
    }
}
