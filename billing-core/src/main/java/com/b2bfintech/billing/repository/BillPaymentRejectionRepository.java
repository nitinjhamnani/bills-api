package com.b2bfintech.billing.repository;

import com.b2bfintech.billing.entity.BillPaymentRejection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BillPaymentRejectionRepository extends JpaRepository<BillPaymentRejection, UUID> {
    List<BillPaymentRejection> findByBillPaymentId(UUID billPaymentId);
}
