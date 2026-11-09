package com.b2bfintech.billing.repository;

import com.b2bfintech.billing.entity.VendorReimbursement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface VendorReimbursementRepository extends JpaRepository<VendorReimbursement, UUID> {
    List<VendorReimbursement> findAllByOrderByPaidAtDesc();

    List<VendorReimbursement> findByVendorClientIdOrderByPaidAtDesc(UUID vendorClientId);

    List<VendorReimbursement> findByPaidAtGreaterThanEqualAndPaidAtLessThanOrderByPaidAtDesc(Instant from, Instant to);

    List<VendorReimbursement> findByVendorClientIdAndPaidAtGreaterThanEqualAndPaidAtLessThanOrderByPaidAtDesc(
            UUID vendorClientId, Instant from, Instant to);
}
