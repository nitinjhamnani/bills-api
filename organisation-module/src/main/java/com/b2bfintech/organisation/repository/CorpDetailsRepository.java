package com.b2bfintech.organisation.repository;

import com.b2bfintech.organisation.entity.CorpDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CorpDetailsRepository extends JpaRepository<CorpDetails, String> {
    boolean existsByOnboardedByAndOnboardedById(CorpDetails.OnboardedBy onboardedBy, String onboardedById);

    List<CorpDetails> findByOnboardedByAndOnboardedByIdOrderByNameAsc(
            CorpDetails.OnboardedBy onboardedBy, String onboardedById);
}
