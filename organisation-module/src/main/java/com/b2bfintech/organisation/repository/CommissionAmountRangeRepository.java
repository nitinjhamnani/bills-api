package com.b2bfintech.organisation.repository;

import com.b2bfintech.organisation.entity.CommissionAmountRange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CommissionAmountRangeRepository extends JpaRepository<CommissionAmountRange, UUID> {
    List<CommissionAmountRange> findAllByOrderByMinAmountAsc();
}
