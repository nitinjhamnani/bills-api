package com.b2bfintech.billing.repository;

import com.b2bfintech.billing.entity.VendorPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VendorPaymentRepository extends JpaRepository<VendorPayment, UUID> {
    Optional<VendorPayment> findByBillPaymentId(UUID billPaymentId);

    List<VendorPayment> findByBillPaymentIdIn(Collection<UUID> billPaymentIds);

    List<VendorPayment> findByPaymentStatus(VendorPayment.PaymentStatus paymentStatus);
}
