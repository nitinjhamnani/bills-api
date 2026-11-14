package com.b2bfintech.organisation.repository;

import com.b2bfintech.organisation.entity.BranchMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BranchMembershipRepository extends JpaRepository<BranchMembership, BranchMembership.Key> {

    boolean existsByUserIdAndBranchId(String userId, String branchId);

    Optional<BranchMembership> findByUserIdAndBranchId(String userId, String branchId);

    @Query("SELECT m.branchId FROM BranchMembership m WHERE m.userId = :userId")
    List<String> findBranchIdsByUserId(@Param("userId") String userId);

    @Query("SELECT m.userId FROM BranchMembership m WHERE m.branchId = :branchId")
    List<String> findUserIdsByBranchId(@Param("branchId") String branchId);

    long countByBranchId(String branchId);
}
