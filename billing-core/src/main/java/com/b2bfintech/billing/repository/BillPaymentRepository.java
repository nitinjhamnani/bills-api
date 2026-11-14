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

    @Query("select p from BillPayment p where p.corpPaymentOn between :from and :to order by p.corpPaymentOn desc")
    List<BillPayment> findByClientPaidAtBetweenOrderByClientPaidAtDesc(@Param("from") Instant from, @Param("to") Instant to);

    default List<BillPayment> findByClientIdAndCreatedAtBetweenOrderByCreatedAtDesc(String corpId, Instant from, Instant to) {
        return findByClientIdAndClientPaidAtBetweenOrderByClientPaidAtDesc(corpId, from, to);
    }

    default List<BillPayment> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant from, Instant to) {
        return findByClientPaidAtBetweenOrderByClientPaidAtDesc(from, to);
    }

    default List<BillPayment> findByClientIdAndOrgPaidAtBetweenOrderByOrgPaidAtDesc(String corpId, Instant from, Instant to) {
        return findByClientIdOrderByCreatedAtDesc(corpId).stream()
                .filter(p -> within(p.getOrgPaidAt(), from, to))
                .sorted(Comparator.comparing(BillPayment::getOrgPaidAt).reversed())
                .toList();
    }

    default List<BillPayment> findByOrgPaidAtBetweenOrderByOrgPaidAtDesc(Instant from, Instant to) {
        return findAllNewestFirst().stream()
                .filter(p -> within(p.getOrgPaidAt(), from, to))
                .sorted(Comparator.comparing(BillPayment::getOrgPaidAt).reversed())
                .toList();
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

    @Query("""
            select p.corpId, count(p), coalesce(sum(p.billAmount), 0)
            from BillPayment p where p.corpPaymentOn between :from and :to group by p.corpId
            """)
    List<Object[]> sumClientPaidByClient(@Param("from") Instant from, @Param("to") Instant to);

    default List<Object[]> sumOrgSettledByClient(Instant from, Instant to) {
        java.util.Map<String, Object[]> rows = new java.util.LinkedHashMap<>();
        for (BillPayment p : findByOrgPaidAtBetweenOrderByOrgPaidAtDesc(from, to)) {
            Object[] row = rows.computeIfAbsent(p.getCorpId(), id -> new Object[]{id, 0L, BigDecimal.ZERO});
            row[1] = (Long) row[1] + 1;
            row[2] = ((BigDecimal) row[2]).add(p.getBillAmount() == null ? BigDecimal.ZERO : p.getBillAmount());
        }
        return List.copyOf(rows.values());
    }

    /** Per corporate: payment count, paid to the biller, still with Paytrix or a vendor. */
    default List<Object[]> aggregatePortfolioByClient() {
        java.util.Map<String, Object[]> rows = new java.util.LinkedHashMap<>();
        for (BillPayment p : findAllNewestFirst()) {
            Object[] row = rows.computeIfAbsent(p.getCorpId(),
                    id -> new Object[]{id, 0L, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal amount = p.getBillAmount() == null ? BigDecimal.ZERO : p.getBillAmount();
            row[1] = (Long) row[1] + 1;
            if (p.getOrgStatus() == BillPayment.OrgStatus.PAID) {
                row[2] = ((BigDecimal) row[2]).add(amount);
            } else {
                row[4] = ((BigDecimal) row[4]).add(amount);
            }
        }
        return List.copyOf(rows.values());
    }

    private static boolean within(Instant value, Instant from, Instant to) {
        return value != null && !value.isBefore(from) && !value.isAfter(to);
    }
}
