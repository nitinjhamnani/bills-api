package com.b2bfintech.platform.repository;

import com.b2bfintech.platform.entity.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    List<AuthSession> findByUserIdAndRevokedOnIsNull(String userId);

    List<AuthSession> findByClientIdAndRevokedOnIsNull(String clientId);

    Optional<AuthSession> findByRefreshHash(String refreshHash);

    Optional<AuthSession> findByPrevRefreshHashAndRevokedOnIsNull(String prevRefreshHash);
}
