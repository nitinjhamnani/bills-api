package com.b2bfintech.organisation.repository;

import com.b2bfintech.organisation.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByPhoneNumber(String phoneNumber);

    List<AppUser> findByClientId(UUID clientId);
}
