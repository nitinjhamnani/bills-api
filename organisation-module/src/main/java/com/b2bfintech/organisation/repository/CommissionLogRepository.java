package com.b2bfintech.organisation.repository;

import com.b2bfintech.organisation.entity.CommissionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CommissionLogRepository extends JpaRepository<CommissionLog, UUID> {

    List<CommissionLog> findByCommissionIdOrderByCreatedAtDesc(UUID commissionId);
}
