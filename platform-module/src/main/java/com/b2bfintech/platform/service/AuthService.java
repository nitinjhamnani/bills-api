package com.b2bfintech.platform.service;

import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.UnauthorizedException;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.common.security.JwtService;
import com.b2bfintech.common.security.Portal;
import com.b2bfintech.organisation.entity.AppUser;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.entity.UserTypes;
import com.b2bfintech.organisation.repository.AppUserRepository;
import com.b2bfintech.organisation.repository.BranchMembershipRepository;
import com.b2bfintech.organisation.repository.ClientRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Production password authentication, refresh rotation, and single-session enforcement.
 * Only stored BCrypt password hashes authenticate — no demo or fallback passwords exist.
 */
@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final BranchMembershipRepository membershipRepository;
    private final ClientRepository clientRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionService authSessionService;
    private final LoginAttemptLimiter loginAttemptLimiter;

    public AuthService(
            AppUserRepository appUserRepository,
            BranchMembershipRepository membershipRepository,
            ClientRepository clientRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            AuthSessionService authSessionService,
            LoginAttemptLimiter loginAttemptLimiter) {
        this.appUserRepository = appUserRepository;
        this.membershipRepository = membershipRepository;
        this.clientRepository = clientRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.authSessionService = authSessionService;
        this.loginAttemptLimiter = loginAttemptLimiter;
    }

    public LoginResult loginWithPassword(String identifier, String password, String userAgent, String ipAddress) {
        if (identifier == null || identifier.isBlank() || password == null || password.isBlank()) {
            throw new BadRequestException("identifier and password are required");
        }
        String trimmedIdentifier = identifier.trim();
        loginAttemptLimiter.assertAllowed(trimmedIdentifier, ipAddress);

        AppUser user = appUserRepository.findByPhoneNumberOrEmail(trimmedIdentifier).orElse(null);
        if (user == null
                || user.getPassword() == null
                || user.getPassword().isBlank()
                || !passwordEncoder.matches(password, user.getPassword())) {
            loginAttemptLimiter.recordFailure(trimmedIdentifier, ipAddress);
            throw new UnauthorizedException("Invalid phone/email or password");
        }

        if (!user.isActive()) {
            throw new UnauthorizedException("This account is suspended. Contact your admin.");
        }

        String clientId = user.isPaytrix() ? null : user.getUserOrgId();
        if (clientId != null) {
            Client client = clientRepository.findById(clientId)
                    .orElseThrow(() -> new UnauthorizedException("This organisation account does not exist."));
            if (client.getStatus() == Client.ClientStatus.TERMINATED) {
                throw new UnauthorizedException("This organisation account has been terminated.");
            }
            if (client.getStatus() == Client.ClientStatus.SUSPENDED) {
                throw new UnauthorizedException("This organisation account is suspended. Contact Paytrix.");
            }
        }

        loginAttemptLimiter.clearIdentifier(trimmedIdentifier);

        List<String> branches = membershipRepository.findBranchIdsByUserId(user.getUserId());
        String branchId = branches.isEmpty() ? null : branches.get(0);
        Portal portal = UserTypes.portal(user.getUserType());
        String role = UserTypes.tokenRole(user.getUserType(), !branches.isEmpty());

        AuthSessionService.CreatedSession created = authSessionService.createSingleSession(
                user, portal, role, clientId, userAgent, ipAddress);

        String accessToken = jwtService.generateAccessToken(new JwtService.TokenSubject(
                created.session().getSessionId(),
                user.getUserId(),
                user.getPhone(),
                user.getUserId(),
                portal,
                user.getUserType().name(),
                role,
                user.getName(),
                clientId,
                branchId,
                null
        ));

        return new LoginResult(
                created.session().getSessionId(),
                accessToken,
                created.refreshToken(),
                jwtService.getAccessTokenTtlSeconds(),
                user,
                portal,
                role,
                clientId,
                branchId
        );
    }

    public LoginResult refresh(String refreshToken) {
        AuthSessionService.RotatedSession rotated = authSessionService.rotateRefreshToken(refreshToken);
        AppUser user = rotated.user();
        Portal portal = rotated.session().getPortal();

        String accessToken = jwtService.generateAccessToken(new JwtService.TokenSubject(
                rotated.session().getSessionId(),
                user.getUserId(),
                user.getPhone(),
                user.getUserId(),
                portal,
                user.getUserType().name(),
                rotated.role(),
                user.getName(),
                rotated.clientId(),
                rotated.branchId(),
                null
        ));

        return new LoginResult(
                rotated.session().getSessionId(),
                accessToken,
                rotated.refreshToken(),
                jwtService.getAccessTokenTtlSeconds(),
                user,
                portal,
                rotated.role(),
                rotated.clientId(),
                rotated.branchId()
        );
    }

    public void logout(UUID sessionId) {
        authSessionService.revokeSession(sessionId, "LOGOUT");
    }

    public void changePassword(AuthenticatedUser principal, String currentPassword, String newPassword) {
        if (principal == null || principal.userId() == null) {
            throw new UnauthorizedException("Sign in to continue");
        }
        if (currentPassword == null || currentPassword.isBlank()) {
            throw new BadRequestException("Current password is required");
        }
        if (newPassword == null || newPassword.length() < 6 || newPassword.length() > 72) {
            throw new BadRequestException("New password must be 6 to 72 characters");
        }
        AppUser user = appUserRepository.findById(principal.userId())
                .orElseThrow(() -> new UnauthorizedException("Sign in to continue"));
        if (user.getPassword() == null
                || user.getPassword().isBlank()
                || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new BadRequestException("New password must be different from your current password");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        appUserRepository.save(user);
    }

    public SessionState currentSession(AuthenticatedUser principal) {
        if (principal == null || principal.sessionId() == null) {
            throw new UnauthorizedException("Sign in to continue");
        }
        AppUser user = appUserRepository.findById(principal.userId())
                .orElseThrow(() -> new UnauthorizedException("Sign in to continue"));
        if (!user.isActive()) {
            throw new UnauthorizedException("This account is suspended.");
        }
        List<String> branches = membershipRepository.findBranchIdsByUserId(user.getUserId());
        String branchId = branches.isEmpty() ? null : branches.get(0);
        Portal portal = UserTypes.portal(user.getUserType());
        String role = UserTypes.tokenRole(user.getUserType(), !branches.isEmpty());
        String clientId = user.isPaytrix() ? null : user.getUserOrgId();
        return new SessionState(principal.sessionId(), user, portal, role, clientId, branchId);
    }

    public record LoginResult(
            UUID sessionId,
            String accessToken,
            String refreshToken,
            long expiresInSeconds,
            AppUser user,
            Portal portal,
            String role,
            String clientId,
            String branchId
    ) {
    }

    public record SessionState(
            UUID sessionId,
            AppUser user,
            Portal portal,
            String role,
            String clientId,
            String branchId
    ) {
    }
}
