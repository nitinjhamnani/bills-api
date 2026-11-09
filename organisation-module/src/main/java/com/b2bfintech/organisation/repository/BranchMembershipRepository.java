package com.b2bfintech.organisation.repository;

import com.b2bfintech.organisation.entity.BranchMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BranchMembershipRepository extends JpaRepository<BranchMembership, UUID> {

    boolean existsByUserIdAndBranchId(UUID userId, UUID branchId);

    Optional<BranchMembership> findByUserIdAndBranchId(UUID userId, UUID branchId);

    @Query("SELECT m.branchId FROM BranchMembership m WHERE m.userId = :userId")
    List<UUID> findBranchIdsByUserId(@Param("userId") UUID userId);

    @Query("SELECT m.userId FROM BranchMembership m WHERE m.branchId = :branchId")
    List<UUID> findUserIdsByBranchId(@Param("branchId") UUID branchId);

    long countByBranchId(UUID branchId);
}
