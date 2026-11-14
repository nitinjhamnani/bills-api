package com.b2bfintech.wallet.repository;

import com.b2bfintech.wallet.entity.VendorTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VendorTransactionRepository extends JpaRepository<VendorTransaction, UUID> {
    List<VendorTransaction> findByVendorIdOrderByCreatedAtDesc(String vendorId);
}
