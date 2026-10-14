package com.b2bfintech.billing.repository;

import com.b2bfintech.billing.entity.BillPaymentLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BillPaymentLogRepository extends JpaRepository<BillPaymentLog, UUID> {
    List<BillPaymentLog> findByBillIdOrderByCreatedAtDesc(UUID billId);
    List<BillPaymentLog> findByBillPaymentIdOrderByCreatedAtDesc(UUID billPaymentId);
}
