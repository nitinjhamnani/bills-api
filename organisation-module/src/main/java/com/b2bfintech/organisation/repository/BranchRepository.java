package com.b2bfintech.organisation.repository;

import com.b2bfintech.organisation.entity.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface BranchRepository extends JpaRepository<Branch, String> {
    List<Branch> findBySourceIdOrderByNameAsc(String sourceId);

    Optional<Branch> findBySourceIdAndCode(String sourceId, String code);

    long countBySourceId(String sourceId);

    @Query("select b.sourceId as sourceId, count(b) as branchCount from Branch b group by b.sourceId")
    List<BranchCount> countGroupedBySourceId();

    interface BranchCount {
        String getSourceId();
        long getBranchCount();
    }
}
