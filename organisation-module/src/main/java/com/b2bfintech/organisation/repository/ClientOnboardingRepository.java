package com.b2bfintech.organisation.repository;

import com.b2bfintech.organisation.entity.ClientOnboarding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ClientOnboardingRepository extends JpaRepository<ClientOnboarding, UUID> {
}
