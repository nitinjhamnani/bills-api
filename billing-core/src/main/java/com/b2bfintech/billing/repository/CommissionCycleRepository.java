package com.b2bfintech.billing.repository;

import com.b2bfintech.billing.entity.CommissionCycle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CommissionCycleRepository extends JpaRepository<CommissionCycle, UUID> {
}
