package com.b2bfintech.billing.repository;

import com.b2bfintech.billing.entity.BillPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Settlement steps such as picked up or verified are worked out from the stored columns, so the
 * lists filtered by those steps are filtered after loading.
 */
public interface BillPaymentRepository extends JpaRepository<BillPayment, UUID> {

    @Query("select p from BillPayment p order by p.corpPaymentOn desc")
    List<BillPayment> findAllNewestFirst();

    @Query("""
            select new com.b2bfintech.billing.repository.BillPaymentRow(
                p.id,
                p.billFetch.billFetchId,
                p.billAmount,
                p.corpPaymentOn,
                p.corpId,
                p.corpName,
                p.corpPaidUserId,
                p.corpPaymentStatus,
                p.billAssignedTo,
                p.billAssignedToId,
                p.billAssignedToName,
                p.billAssignedToStatus,
                p.billAssignedOn,
                p.billPaidOn,
                p.transactionReferenceNumber,
                p.bbpsExternalReference,
                p.paymentProof,
                p.verifiedBy,
                p.verifiedById,
                p.verifiedOn,
                p.verified
            )
            from BillPayment p
            order by p.corpPaymentOn desc
            """)
    List<BillPaymentRow> findAllTableRowsNewestFirst();

    @Query("select p from BillPayment p where p.billFetch.billId = :billId order by p.corpPaymentOn desc")
    List<BillPayment> findByBillIdOrderByCreatedAtDesc(@Param("billId") String billId);

    @Query("select p from BillPayment p where p.corpId = :corpId order by p.corpPaymentOn desc")
    List<BillPayment> findByClientIdOrderByCreatedAtDesc(@Param("corpId") String corpId);

    Optional<BillPayment> findByBillFetchBillFetchId(UUID billFetchId);

    List<BillPayment> findByBillAssignedToAndBillAssignedToIdOrderByBillAssignedOnDesc(
            BillPayment.AssignedTo assignedTo, String assignedToId);

    List<BillPayment> findByBillAssignedTo(BillPayment.AssignedTo assignedTo);

    default List<BillPayment> findAssignedToVendor(String vendorId) {
        return findByBillAssignedToAndBillAssignedToIdOrderByBillAssignedOnDesc(
                BillPayment.AssignedTo.VENDOR, vendorId);
    }

    default List<BillPayment> findAllWithVendors() {
        return findByBillAssignedTo(BillPayment.AssignedTo.VENDOR);
    }

    @Query("select p from BillPayment p where p.corpId = :corpId and p.corpPaymentOn between :from and :to order by p.corpPaymentOn desc")
    List<BillPayment> findByClientIdAndClientPaidAtBetweenOrderByClientPaidAtDesc(
            @Param("corpId") String corpId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select p from BillPayment p
            where p.corpPaymentOn >= :from and p.corpPaymentOn < :to
            """)
    List<BillPayment> findRaisedBetween(@Param("from") Instant from, @Param("to") Instant to);

    /** Bills settled with the biller in the window: Paytrix paid, or a vendor payment Paytrix verified. */
    @Query("""
            select p from BillPayment p
            where (p.billAssignedTo = :paytrix and p.billPaidOn >= :from and p.billPaidOn < :to)
               or (p.billAssignedTo = :vendor and p.verified = true
                   and p.verifiedOn >= :from and p.verifiedOn < :to)
            """)
    List<BillPayment> findSettledBetween(
            @Param("paytrix") BillPayment.AssignedTo paytrix,
            @Param("vendor") BillPayment.AssignedTo vendor,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
            select p from BillPayment p
            where p.billAssignedTo = :vendor
              and p.billAssignedOn >= :from and p.billAssignedOn < :to
            """)
    List<BillPayment> findAssignedToVendorsBetween(
            @Param("vendor") BillPayment.AssignedTo vendor,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
            select p from BillPayment p
            where p.billAssignedTo = :vendor
              and p.billPaidOn is not null
              and p.billPaidOn >= :from and p.billPaidOn < :to
            """)
    List<BillPayment> findVendorPaidBetween(
            @Param("vendor") BillPayment.AssignedTo vendor,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /** Vendor-held bills the vendor has paid and Paytrix has verified, by the date the vendor paid. */
    @Query("""
            select p from BillPayment p
            where p.billAssignedTo = :assignedTo
              and p.verified = true
              and p.billPaidOn is not null
              and p.billPaidOn >= :from
              and p.billPaidOn < :to
            order by p.billPaidOn desc
            """)
    List<BillPayment> findVerifiedVendorPaidByPaidOn(
            @Param("assignedTo") BillPayment.AssignedTo assignedTo,
            @Param("from") Instant from,
            @Param("to") Instant to);

    default List<BillPayment> findByClientIdAndCreatedAtBetweenOrderByCreatedAtDesc(String corpId, Instant from, Instant to) {
        return findByClientIdAndClientPaidAtBetweenOrderByClientPaidAtDesc(corpId, from, to);
    }

    default List<BillPayment> findByOrgStatusInOrderByCreatedAtDesc(Collection<BillPayment.OrgStatus> statuses) {
        return findAllNewestFirst().stream().filter(p -> statuses.contains(p.getOrgStatus())).toList();
    }

    default List<BillPayment> findByAssignedVendorClientIdAndOrgStatusInOrderByAssignedAtDesc(
            String vendorId, Collection<BillPayment.OrgStatus> statuses) {
        return findAssignedToVendor(vendorId).stream().filter(p -> statuses.contains(p.getOrgStatus())).toList();
    }

    default List<BillPayment> findByVendorStageOrderByVendorMarkedPaidAtAsc(BillPayment.VendorStage stage) {
        return findAllWithVendors().stream()
                .filter(p -> p.getVendorStage() == stage)
                .sorted(Comparator.comparing(BillPayment::getBillPaidOn, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    default List<BillPayment> findByVendorStageOrderByBbpsVerifiedAtAsc(BillPayment.VendorStage stage) {
        return findAllWithVendors().stream()
                .filter(p -> p.getVendorStage() == stage)
                .sorted(Comparator.comparing(BillPayment::getVerifiedOn, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    default long countWaitingWithVendor(String vendorId) {
        return findAssignedToVendor(vendorId).stream()
                .filter(p -> p.getOrgStatus() == BillPayment.OrgStatus.PENDING)
                .count();
    }

    default long countByOrgStatus(BillPayment.OrgStatus status) {
        return findAllNewestFirst().stream().filter(p -> p.getOrgStatus() == status).count();
    }

    default BigDecimal sumAmountByOrgStatus(BillPayment.OrgStatus status) {
        return findAllNewestFirst().stream()
                .filter(p -> p.getOrgStatus() == status)
                .map(BillPayment::getBillAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

}
