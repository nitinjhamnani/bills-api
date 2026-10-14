package com.b2bfintech.billing.repository;

import com.b2bfintech.billing.entity.Bill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillRepository extends JpaRepository<Bill, UUID> {
    List<Bill> findByClientIdOrderByCreatedAtDesc(UUID clientId);
    List<Bill> findAllByOrderByCreatedAtDesc();
    List<Bill> findByAutoFetchEnabledTrue();

    Optional<Bill> findByClientIdAndBillServiceCodeAndCustomerBillAccountNumber(
            UUID clientId, String billServiceCode, String customerBillAccountNumber);

    long countByBillStatus(Bill.BillStatus billStatus);
    long countByClientIdAndBillStatus(UUID clientId, Bill.BillStatus billStatus);
    long countByClientId(UUID clientId);
    long countByClientIdAndPaymentStatus(UUID clientId, Bill.PaymentStatus paymentStatus);

    @Query("select coalesce(sum(b.amount), 0) from Bill b where b.clientId = :clientId and b.billStatus = :status")
    BigDecimal sumAmountByClientIdAndBillStatus(UUID clientId, Bill.BillStatus status);

    @Query("select coalesce(sum(b.amount), 0) from Bill b where b.clientId = :clientId and b.paymentStatus = :status")
    BigDecimal sumAmountByClientIdAndPaymentStatus(UUID clientId, Bill.PaymentStatus status);

    @Query("select coalesce(sum(b.amount), 0) from Bill b where b.clientId = :clientId")
    BigDecimal sumAmountByClientId(UUID clientId);
}
