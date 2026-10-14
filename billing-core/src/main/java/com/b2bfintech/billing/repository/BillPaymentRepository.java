package com.b2bfintech.billing.repository;

import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillPaymentRepository extends JpaRepository<BillPayment, UUID> {
    List<BillPayment> findByBillIdOrderByCreatedAtDesc(UUID billId);
    List<BillPayment> findByClientIdOrderByCreatedAtDesc(UUID clientId);
    List<BillPayment> findByClientIdAndPaymentStatusOrderByCreatedAtDesc(UUID clientId, Bill.PaymentStatus paymentStatus);
    List<BillPayment> findByOrgStatusInOrderByCreatedAtDesc(Collection<BillPayment.OrgStatus> orgStatuses);
    List<BillPayment> findByClientIdAndOrgStatusInOrderByCreatedAtDesc(UUID clientId, Collection<BillPayment.OrgStatus> orgStatuses);
    Optional<BillPayment> findByIdempotencyKey(String idempotencyKey);

    long countByOrgStatus(BillPayment.OrgStatus orgStatus);
    long countByClientIdAndOrgStatus(UUID clientId, BillPayment.OrgStatus orgStatus);
    long countByClientId(UUID clientId);

    @Query("select coalesce(sum(p.amount), 0) from BillPayment p where p.orgStatus = :orgStatus")
    BigDecimal sumAmountByOrgStatus(BillPayment.OrgStatus orgStatus);

    @Query("select coalesce(sum(p.amount), 0) from BillPayment p where p.clientId = :clientId and p.orgStatus = :orgStatus")
    BigDecimal sumAmountByClientIdAndOrgStatus(UUID clientId, BillPayment.OrgStatus orgStatus);

    @Query("select coalesce(sum(p.amount), 0) from BillPayment p where p.clientId = :clientId and p.billStatus = :billStatus")
    BigDecimal sumAmountByClientIdAndBillStatus(UUID clientId, Bill.BillStatus billStatus);

    /** Report queries: every bill payment cycle *created* within a window, newest first. */
    List<BillPayment> findByClientIdAndCreatedAtBetweenOrderByCreatedAtDesc(UUID clientId, Instant from, Instant to);
    List<BillPayment> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant from, Instant to);
    List<BillPayment> findByClientIdAndClientPaidAtBetweenOrderByClientPaidAtDesc(UUID clientId, Instant from, Instant to);
    List<BillPayment> findByClientPaidAtBetweenOrderByClientPaidAtDesc(Instant from, Instant to);
    List<BillPayment> findByClientIdAndOrgPaidAtBetweenOrderByOrgPaidAtDesc(UUID clientId, Instant from, Instant to);
    List<BillPayment> findByOrgPaidAtBetweenOrderByOrgPaidAtDesc(Instant from, Instant to);

    @Query("""
            select p.clientId, count(p),
                   coalesce(sum(case when p.billStatus = 'PAID' then p.amount else 0 end), 0),
                   coalesce(sum(case when p.paymentStatus = 'PENDING' then p.amount else 0 end), 0),
                   coalesce(sum(case when p.orgStatus = 'PENDING' or p.orgStatus = 'IN_PROCESS' then p.amount else 0 end), 0)
            from BillPayment p group by p.clientId
            """)
    List<Object[]> aggregatePortfolioByClient();

    @Query("select p.clientId, count(p), coalesce(sum(p.amount), 0) from BillPayment p where p.clientPaidAt between :from and :to group by p.clientId")
    List<Object[]> sumClientPaidByClient(Instant from, Instant to);

    @Query("select p.clientId, count(p), coalesce(sum(p.amount), 0) from BillPayment p where p.orgPaidAt between :from and :to group by p.clientId")
    List<Object[]> sumOrgSettledByClient(Instant from, Instant to);

    long countByBillStatusAndCreatedAtBetween(Bill.BillStatus billStatus, Instant from, Instant to);

    @Query("select coalesce(sum(p.amount), 0) from BillPayment p where p.billStatus = :billStatus and p.createdAt between :from and :to")
    BigDecimal sumAmountByBillStatusAndCreatedAtBetween(Bill.BillStatus billStatus, Instant from, Instant to);
}
