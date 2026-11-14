package com.b2bfintech.organisation.repository;

import com.b2bfintech.organisation.entity.Commission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CommissionRepository extends JpaRepository<Commission, UUID> {
    List<Commission> findAllByOrderByCreatedAtDesc();

    List<Commission> findByVendorIdOrderByCreatedAtDesc(String vendorId);

    List<Commission> findByCategoryIdAndOperatorId(UUID categoryId, String operatorId);
}
