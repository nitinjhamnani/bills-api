package com.b2bfintech.billing.repository;

import com.b2bfintech.billing.entity.BillDispute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BillDisputeRepository extends JpaRepository<BillDispute, UUID> {
    List<BillDispute> findByBillPaymentId(UUID billPaymentId);
    List<BillDispute> findAllByOrderByCreatedAtDesc();
}
