package com.b2bfintech.organisation.repository;

import com.b2bfintech.organisation.entity.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BranchRepository extends JpaRepository<Branch, UUID> {
    List<Branch> findByClientIdOrderByNameAsc(UUID clientId);

    Optional<Branch> findByClientIdAndCode(UUID clientId, String code);

    long countByClientId(UUID clientId);

    @Query("select b.clientId as clientId, count(b) as branchCount from Branch b group by b.clientId")
    List<BranchCount> countGroupedByClientId();

    interface BranchCount {
        UUID getClientId();
        long getBranchCount();
    }
}
