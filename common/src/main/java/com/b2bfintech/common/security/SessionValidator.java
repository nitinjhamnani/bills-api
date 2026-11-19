package com.b2bfintech.common.security;

import java.util.Optional;
import java.util.UUID;

/**
 * Validates that a JWT's {@code sid} claim still corresponds to an active, unrevoked,
 * non-expired server-side session whose user and organisation remain active.
 */
public interface SessionValidator {

    record ActiveSession(
            UUID sessionId,
            String userId,
            Portal portal,
            String userType,
            String role,
            String clientId
    ) {
    }

    Optional<ActiveSession> validateAndTouch(UUID sessionId, String expectedUserId, String expectedUserType);
}
