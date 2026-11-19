package com.b2bfintech.platform.service;

import com.b2bfintech.common.exception.UnauthorizedException;
import com.b2bfintech.common.security.Portal;
import com.b2bfintech.common.security.SessionValidator;
import com.b2bfintech.organisation.entity.AppUser;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.entity.UserTypes;
import com.b2bfintech.organisation.repository.AppUserRepository;
import com.b2bfintech.organisation.repository.BranchMembershipRepository;
import com.b2bfintech.organisation.repository.ClientRepository;
import com.b2bfintech.platform.entity.AuthSession;
import com.b2bfintech.platform.repository.AuthSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages server-side user sessions ({@code auth_session}):
 * <ul>
 *   <li>Strict single active session per user (new login revokes prior sessions).</li>
 *   <li>30-minute idle timeout and 12-hour absolute lifetime.</li>
 *   <li>Refresh-token rotation with reuse theft detection.</li>
 *   <li>Instant session revocation on logout, user suspension, password reset, role change, or client suspension/termination.</li>
 * </ul>
 */
@Service
public class AuthSessionService implements SessionValidator {

    private static final Duration CACHE_TTL = Duration.ofSeconds(15);
    private static final Duration TOUCH_PERSIST_INTERVAL = Duration.ofSeconds(60);

    private final AuthSessionRepository sessionRepository;
    private final AppUserRepository appUserRepository;
    private final BranchMembershipRepository membershipRepository;
    private final ClientRepository clientRepository;
    private final Duration idleTimeout;
    private final Duration absoluteTimeout;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentHashMap<UUID, CachedEntry> validationCache = new ConcurrentHashMap<>();

    private record CachedEntry(ActiveSession activeSession, Instant cachedUntil) {
    }

    public record CreatedSession(AuthSession session, String refreshToken) {
    }

    public record RotatedSession(
            AuthSession session,
            AppUser user,
            String refreshToken,
            String role,
            String clientId,
            String branchId
    ) {
    }

    @Autowired
    public AuthSessionService(
            AuthSessionRepository sessionRepository,
            AppUserRepository appUserRepository,
            BranchMembershipRepository membershipRepository,
            ClientRepository clientRepository,
            @Value("${app.auth.idle-timeout-minutes:30}") long idleTimeoutMinutes,
            @Value("${app.auth.absolute-timeout-hours:12}") long absoluteTimeoutHours) {
        this(
                sessionRepository,
                appUserRepository,
                membershipRepository,
                clientRepository,
                Duration.ofMinutes(idleTimeoutMinutes),
                Duration.ofHours(absoluteTimeoutHours),
                Clock.systemUTC()
        );
    }

    public AuthSessionService(
            AuthSessionRepository sessionRepository,
            AppUserRepository appUserRepository,
            BranchMembershipRepository membershipRepository,
            ClientRepository clientRepository,
            Duration idleTimeout,
            Duration absoluteTimeout,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.appUserRepository = appUserRepository;
        this.membershipRepository = membershipRepository;
        this.clientRepository = clientRepository;
        this.idleTimeout = idleTimeout;
        this.absoluteTimeout = absoluteTimeout;
        this.clock = clock;
    }

    @Transactional
    public CreatedSession createSingleSession(
            AppUser user,
            Portal portal,
            String roleName,
            String clientId,
            String userAgent,
            String ipAddress) {
        Instant now = clock.instant();
        revokeAllForUserInternal(user.getUserId(), "NEW_LOGIN", now);

        String rawRefresh = generateRawRefreshToken();
        AuthSession session = new AuthSession();
        session.setSessionId(UUID.randomUUID());
        session.setUserId(user.getUserId());
        session.setPortal(portal);
        session.setUserType(user.getUserType().name());
        session.setRoleName(roleName);
        session.setClientId(clientId);
        session.setRefreshHash(sha256Hex(rawRefresh));
        session.setPrevRefreshHash(null);
        session.setCreatedOn(now);
        session.setLastSeenOn(now);
        session.setExpiresOn(now.plus(absoluteTimeout));
        session.setUserAgent(truncate(userAgent, 300));
        session.setIpAddress(truncate(ipAddress, 64));
        session = sessionRepository.save(session);

        ActiveSession active = new ActiveSession(
                session.getSessionId(),
                session.getUserId(),
                session.getPortal(),
                session.getUserType(),
                session.getRoleName(),
                session.getClientId()
        );
        validationCache.put(session.getSessionId(), new CachedEntry(active, now.plus(CACHE_TTL)));
        return new CreatedSession(session, rawRefresh);
    }

    @Override
    @Transactional
    public Optional<ActiveSession> validateAndTouch(UUID sessionId, String expectedUserId, String expectedUserType) {
        if (sessionId == null || expectedUserId == null || expectedUserType == null) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        CachedEntry cached = validationCache.get(sessionId);
        if (cached != null && now.isBefore(cached.cachedUntil())) {
            ActiveSession active = cached.activeSession();
            if (expectedUserId.equals(active.userId()) && expectedUserType.equals(active.userType())) {
                return Optional.of(active);
            }
            validationCache.remove(sessionId);
            return Optional.empty();
        }

        Optional<AuthSession> found = sessionRepository.findById(sessionId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        AuthSession session = found.get();
        if (session.isRevoked() || !expectedUserId.equals(session.getUserId())) {
            validationCache.remove(sessionId);
            return Optional.empty();
        }
        if (!now.isBefore(session.getExpiresOn())) {
            revokeRow(session, "ABSOLUTE_TIMEOUT", now);
            return Optional.empty();
        }
        if (Duration.between(session.getLastSeenOn(), now).compareTo(idleTimeout) > 0) {
            revokeRow(session, "IDLE_TIMEOUT", now);
            return Optional.empty();
        }

        Optional<AppUser> userOpt = appUserRepository.findById(session.getUserId());
        if (userOpt.isEmpty() || !userOpt.get().isActive()) {
            revokeRow(session, "USER_INACTIVE", now);
            return Optional.empty();
        }
        AppUser user = userOpt.get();
        if (!expectedUserType.equals(user.getUserType().name())
                || !session.getUserType().equals(user.getUserType().name())) {
            revokeRow(session, "USER_TYPE_CHANGED", now);
            return Optional.empty();
        }

        String clientId = user.isPaytrix() ? null : user.getUserOrgId();
        if (clientId != null && !isClientAllowedToRemainSignedIn(clientId)) {
            revokeRow(session, "CLIENT_INACTIVE", now);
            return Optional.empty();
        }

        List<String> branches = membershipRepository.findBranchIdsByUserId(user.getUserId());
        String liveRole = UserTypes.tokenRole(user.getUserType(), !branches.isEmpty());
        Portal livePortal = UserTypes.portal(user.getUserType());
        if (livePortal != session.getPortal() || !liveRole.equals(session.getRoleName())) {
            revokeRow(session, "ROLE_CHANGED", now);
            return Optional.empty();
        }

        if (Duration.between(session.getLastSeenOn(), now).compareTo(TOUCH_PERSIST_INTERVAL) >= 0) {
            session.setLastSeenOn(now);
            sessionRepository.save(session);
        }

        ActiveSession active = new ActiveSession(
                session.getSessionId(),
                user.getUserId(),
                livePortal,
                user.getUserType().name(),
                liveRole,
                clientId
        );
        validationCache.put(sessionId, new CachedEntry(active, now.plus(CACHE_TTL)));
        return Optional.of(active);
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public RotatedSession rotateRefreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new UnauthorizedException("Session expired. Sign in again.");
        }
        Instant now = clock.instant();
        String hash = sha256Hex(rawRefreshToken.trim());

        Optional<AuthSession> sessionOpt = sessionRepository.findByRefreshHash(hash);
        if (sessionOpt.isEmpty()) {
            sessionRepository.findByPrevRefreshHashAndRevokedOnIsNull(hash)
                    .ifPresent(reused -> revokeRow(reused, "REFRESH_REUSE_DETECTED", now));
            throw new UnauthorizedException("Session expired. Sign in again.");
        }

        AuthSession session = sessionOpt.get();
        if (session.isRevoked()) {
            validationCache.remove(session.getSessionId());
            throw new UnauthorizedException("Session ended. Sign in again.");
        }
        if (!now.isBefore(session.getExpiresOn())) {
            revokeRow(session, "ABSOLUTE_TIMEOUT", now);
            throw new UnauthorizedException("Session expired. Sign in again.");
        }
        if (Duration.between(session.getLastSeenOn(), now).compareTo(idleTimeout) > 0) {
            revokeRow(session, "IDLE_TIMEOUT", now);
            throw new UnauthorizedException("Session expired due to inactivity. Sign in again.");
        }

        Optional<AppUser> userOpt = appUserRepository.findById(session.getUserId());
        if (userOpt.isEmpty()) {
            revokeRow(session, "USER_MISSING", now);
            throw new UnauthorizedException("Session expired. Sign in again.");
        }
        AppUser user = userOpt.get();
        if (!user.isActive() || !session.getUserType().equals(user.getUserType().name())) {
            revokeRow(session, "USER_INACTIVE_OR_CHANGED", now);
            throw new UnauthorizedException("This account is no longer active. Sign in again.");
        }

        String clientId = user.isPaytrix() ? null : user.getUserOrgId();
        if (clientId != null && !isClientAllowedToRemainSignedIn(clientId)) {
            revokeRow(session, "CLIENT_INACTIVE", now);
            throw new UnauthorizedException("This organisation account is inactive.");
        }

        List<String> branches = membershipRepository.findBranchIdsByUserId(user.getUserId());
        String branchId = branches.isEmpty() ? null : branches.get(0);
        String liveRole = UserTypes.tokenRole(user.getUserType(), !branches.isEmpty());
        Portal livePortal = UserTypes.portal(user.getUserType());

        String nextRawRefresh = generateRawRefreshToken();
        session.setPrevRefreshHash(session.getRefreshHash());
        session.setRefreshHash(sha256Hex(nextRawRefresh));
        session.setRoleName(liveRole);
        session.setPortal(livePortal);
        session.setLastSeenOn(now);
        session = sessionRepository.save(session);

        ActiveSession active = new ActiveSession(
                session.getSessionId(),
                user.getUserId(),
                livePortal,
                user.getUserType().name(),
                liveRole,
                clientId
        );
        validationCache.put(session.getSessionId(), new CachedEntry(active, now.plus(CACHE_TTL)));

        return new RotatedSession(session, user, nextRawRefresh, liveRole, clientId, branchId);
    }

    @Transactional
    public void revokeSession(UUID sessionId, String reason) {
        if (sessionId == null) {
            return;
        }
        Instant now = clock.instant();
        validationCache.remove(sessionId);
        sessionRepository.findById(sessionId).ifPresent(session -> {
            if (!session.isRevoked()) {
                revokeRow(session, reason, now);
            }
        });
    }

    @Transactional
    public void revokeAllForUser(String userId, String reason) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        revokeAllForUserInternal(userId, reason, clock.instant());
    }

    @Transactional
    public void revokeAllForClient(String clientId, String reason) {
        if (clientId == null || clientId.isBlank()) {
            return;
        }
        Instant now = clock.instant();
        List<AuthSession> sessions = sessionRepository.findByClientIdAndRevokedOnIsNull(clientId);
        for (AuthSession session : sessions) {
            session.setRevokedOn(now);
            session.setRevokedReason(reason);
            validationCache.remove(session.getSessionId());
        }
        if (!sessions.isEmpty()) {
            sessionRepository.saveAll(sessions);
        }
    }

    private void revokeAllForUserInternal(String userId, String reason, Instant now) {
        List<AuthSession> activeSessions = sessionRepository.findByUserIdAndRevokedOnIsNull(userId);
        for (AuthSession existing : activeSessions) {
            existing.setRevokedOn(now);
            existing.setRevokedReason(reason);
            validationCache.remove(existing.getSessionId());
        }
        if (!activeSessions.isEmpty()) {
            sessionRepository.saveAll(activeSessions);
        }
    }

    private void revokeRow(AuthSession session, String reason, Instant now) {
        session.setRevokedOn(now);
        session.setRevokedReason(reason);
        sessionRepository.save(session);
        validationCache.remove(session.getSessionId());
    }

    private boolean isClientAllowedToRemainSignedIn(String clientId) {
        return clientRepository.findById(clientId)
                .map(client -> client.getStatus() != Client.ClientStatus.SUSPENDED
                        && client.getStatus() != Client.ClientStatus.TERMINATED)
                .orElse(false);
    }

    private String generateRawRefreshToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String truncate(String value, int maxLen) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLen ? trimmed : trimmed.substring(0, maxLen);
    }
}
