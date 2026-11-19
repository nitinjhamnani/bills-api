package com.b2bfintech.app.security;

import com.b2bfintech.common.exception.TooManyRequestsException;
import com.b2bfintech.common.exception.UnauthorizedException;
import com.b2bfintech.common.security.JwtService;
import com.b2bfintech.organisation.entity.AppUser;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.repository.AppUserRepository;
import com.b2bfintech.organisation.repository.BranchMembershipRepository;
import com.b2bfintech.organisation.repository.ClientRepository;
import com.b2bfintech.platform.entity.AuthSession;
import com.b2bfintech.platform.repository.AuthSessionRepository;
import com.b2bfintech.platform.service.AuthService;
import com.b2bfintech.platform.service.AuthSessionService;
import com.b2bfintech.platform.service.LoginAttemptLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthSessionAndLoginSecurityTest {

    private static final class MutableClock extends Clock {
        private Instant current;

        MutableClock(Instant initial) {
            this.current = initial;
        }

        void advance(Duration delta) {
            this.current = this.current.plus(delta);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }

    private final Map<UUID, AuthSession> sessionStore = new HashMap<>();
    private final Map<String, AppUser> userStore = new HashMap<>();
    private final Map<String, Client> clientStore = new HashMap<>();
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private MutableClock clock;
    private AuthSessionService sessionService;
    private LoginAttemptLimiter rateLimiter;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        sessionStore.clear();
        userStore.clear();
        clientStore.clear();
        clock = new MutableClock(Instant.parse("2026-11-20T00:00:00Z"));

        AuthSessionRepository sessionRepo = mock(AuthSessionRepository.class);
        when(sessionRepo.save(any(AuthSession.class))).thenAnswer(inv -> {
            AuthSession s = inv.getArgument(0);
            sessionStore.put(s.getSessionId(), s);
            return s;
        });
        when(sessionRepo.saveAll(any())).thenAnswer(inv -> {
            Iterable<AuthSession> items = inv.getArgument(0);
            for (AuthSession s : items) {
                sessionStore.put(s.getSessionId(), s);
            }
            return items;
        });
        when(sessionRepo.findById(any(UUID.class))).thenAnswer(inv -> Optional.ofNullable(sessionStore.get(inv.getArgument(0))));
        when(sessionRepo.findByUserIdAndRevokedOnIsNull(anyString())).thenAnswer(inv -> {
            String uid = inv.getArgument(0);
            return sessionStore.values().stream()
                    .filter(s -> uid.equals(s.getUserId()) && !s.isRevoked())
                    .toList();
        });
        when(sessionRepo.findByClientIdAndRevokedOnIsNull(anyString())).thenAnswer(inv -> {
            String cid = inv.getArgument(0);
            return sessionStore.values().stream()
                    .filter(s -> cid.equals(s.getClientId()) && !s.isRevoked())
                    .toList();
        });
        when(sessionRepo.findByRefreshHash(anyString())).thenAnswer(inv -> {
            String hash = inv.getArgument(0);
            return sessionStore.values().stream().filter(s -> hash.equals(s.getRefreshHash())).findFirst();
        });
        when(sessionRepo.findByPrevRefreshHashAndRevokedOnIsNull(anyString())).thenAnswer(inv -> {
            String hash = inv.getArgument(0);
            return sessionStore.values().stream()
                    .filter(s -> !s.isRevoked() && hash.equals(s.getPrevRefreshHash()))
                    .findFirst();
        });

        AppUserRepository userRepo = mock(AppUserRepository.class);
        when(userRepo.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(userStore.get(inv.getArgument(0))));
        when(userRepo.findByPhoneNumberOrEmail(anyString())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            return userStore.values().stream()
                    .filter(u -> id.equals(u.getPhone()) || (u.getEmail() != null && id.equalsIgnoreCase(u.getEmail())))
                    .findFirst();
        });

        BranchMembershipRepository membershipRepo = mock(BranchMembershipRepository.class);
        when(membershipRepo.findBranchIdsByUserId(anyString())).thenReturn(List.of());

        ClientRepository clientRepo = mock(ClientRepository.class);
        when(clientRepo.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(clientStore.get(inv.getArgument(0))));

        JwtService jwtService = new JwtService("unit-test-secret-key-at-least-thirty-two-bytes-long-2026", 15);
        sessionService = new AuthSessionService(
                sessionRepo, userRepo, membershipRepo, clientRepo,
                Duration.ofMinutes(30), Duration.ofHours(12), clock);
        rateLimiter = new LoginAttemptLimiter(clock);
        authService = new AuthService(
                userRepo, membershipRepo, clientRepo, jwtService, passwordEncoder, sessionService, rateLimiter);
    }

    @Test
    void enforcesSingleActiveSessionPerUserOnNewLogin() {
        AppUser vendorAdmin = createVendorUser("USVND1", "9100000001", "VendorPass@1");

        AuthService.LoginResult firstLogin = authService.loginWithPassword(
                "9100000001", "VendorPass@1", "Chrome-1", "10.0.0.1");
        assertTrue(sessionService.validateAndTouch(
                firstLogin.sessionId(), vendorAdmin.getUserId(), "VENDOR_ADMIN").isPresent());

        // Second login for the same user must immediately revoke the first session
        AuthService.LoginResult secondLogin = authService.loginWithPassword(
                "9100000001", "VendorPass@1", "Chrome-2", "10.0.0.2");
        assertNotEquals(firstLogin.sessionId(), secondLogin.sessionId());
        assertTrue(sessionService.validateAndTouch(
                firstLogin.sessionId(), vendorAdmin.getUserId(), "VENDOR_ADMIN").isEmpty());
        assertEquals("NEW_LOGIN", sessionStore.get(firstLogin.sessionId()).getRevokedReason());
        assertTrue(sessionService.validateAndTouch(
                secondLogin.sessionId(), vendorAdmin.getUserId(), "VENDOR_ADMIN").isPresent());
    }

    @Test
    void rotatesRefreshTokensAndRevokesSessionOnTokenReuse() {
        createVendorUser("USVND2", "9100000002", "VendorPass@2");
        AuthService.LoginResult initial = authService.loginWithPassword(
                "9100000002", "VendorPass@2", "Chrome", "10.0.0.1");

        clock.advance(Duration.ofMinutes(10));
        AuthService.LoginResult refreshed = authService.refresh(initial.refreshToken());
        assertEquals(initial.sessionId(), refreshed.sessionId());
        assertNotEquals(initial.refreshToken(), refreshed.refreshToken());

        // Replaying the old rotated refresh token must revoke the entire session
        assertThrows(UnauthorizedException.class, () -> authService.refresh(initial.refreshToken()));
        assertEquals("REFRESH_REUSE_DETECTED", sessionStore.get(initial.sessionId()).getRevokedReason());
        assertTrue(sessionService.validateAndTouch(initial.sessionId(), "USVND2", "VENDOR_ADMIN").isEmpty());
    }

    @Test
    void enforcesIdleAndAbsoluteTimeoutsAndInstantRevocation() {
        AppUser user = createVendorUser("USVND3", "9100000003", "VendorPass@3");
        AuthService.LoginResult login = authService.loginWithPassword(
                "9100000003", "VendorPass@3", "Chrome", "10.0.0.1");

        // Idle > 30 minutes invalidates session
        clock.advance(Duration.ofMinutes(31));
        assertTrue(sessionService.validateAndTouch(login.sessionId(), user.getUserId(), "VENDOR_ADMIN").isEmpty());
        assertEquals("IDLE_TIMEOUT", sessionStore.get(login.sessionId()).getRevokedReason());

        // Fresh login kept active every 20 minutes still expires at 12-hour hard cap
        AuthService.LoginResult second = authService.loginWithPassword(
                "9100000003", "VendorPass@3", "Chrome", "10.0.0.1");
        for (int i = 0; i < 35; i++) {
            clock.advance(Duration.ofMinutes(20));
            sessionService.rotateRefreshToken(
                    sessionStore.get(second.sessionId()) != null && i == 0 ? second.refreshToken() : null
            );
            break;
        }
        clock.advance(Duration.ofHours(12));
        assertTrue(sessionService.validateAndTouch(second.sessionId(), user.getUserId(), "VENDOR_ADMIN").isEmpty());
        assertEquals("ABSOLUTE_TIMEOUT", sessionStore.get(second.sessionId()).getRevokedReason());

        // Suspending client revokes immediately
        AuthService.LoginResult third = authService.loginWithPassword(
                "9100000003", "VendorPass@3", "Chrome", "10.0.0.1");
        sessionService.revokeAllForClient("VEND0001", "CLIENT_SUSPENDED");
        assertTrue(sessionService.validateAndTouch(third.sessionId(), user.getUserId(), "VENDOR_ADMIN").isEmpty());
    }

    @Test
    void rejectsDummyPasswordsBlankPasswordsAndEnforcesRateLimiting() {
        AppUser blankPwdUser = new AppUser();
        blankPwdUser.setUserId("USBLK1");
        blankPwdUser.setPhone("9200000001");
        blankPwdUser.setName("Blank User");
        blankPwdUser.setUserType(AppUser.UserType.SUPER_ADMIN);
        blankPwdUser.setUserOrgId(AppUser.PAYTRIX);
        blankPwdUser.setPassword("");
        blankPwdUser.setActive(true);
        userStore.put("USBLK1", blankPwdUser);

        assertThrows(UnauthorizedException.class,
                () -> authService.loginWithPassword("9200000001", "Password@123", "Chrome", "10.0.0.5"));

        createVendorUser("USRLM1", "9300000001", "RealSecret@99");
        for (int i = 0; i < 5; i++) {
            assertThrows(UnauthorizedException.class,
                    () -> authService.loginWithPassword("9300000001", "Password@123", "Chrome", "10.0.0.9"));
        }
        assertThrows(TooManyRequestsException.class,
                () -> authService.loginWithPassword("9300000001", "RealSecret@99", "Chrome", "10.0.0.9"));
    }

    private AppUser createVendorUser(String userId, String phone, String rawPassword) {
        Client vendor = new Client();
        vendor.setId("VEND0001");
        vendor.setClientName("Vendor One");
        vendor.setClientType(Client.ClientType.VENDOR);
        vendor.setStatus(Client.ClientStatus.ACTIVE);
        clientStore.put("VEND0001", vendor);

        AppUser user = new AppUser();
        user.setUserId(userId);
        user.setPhone(phone);
        user.setName("Vendor Admin");
        user.setUserType(AppUser.UserType.VENDOR_ADMIN);
        user.setUserOrgId("VEND0001");
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setActive(true);
        userStore.put(userId, user);
        return user;
    }
}
