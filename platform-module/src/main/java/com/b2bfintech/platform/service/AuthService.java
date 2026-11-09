package com.b2bfintech.platform.service;

import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.UnauthorizedException;
import com.b2bfintech.common.security.JwtService;
import com.b2bfintech.common.spi.OtpProvider;
import com.b2bfintech.platform.model.UserDirectoryEntry;
import com.b2bfintech.platform.repository.UserDirectoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Password login for Paytrix staff and client users. Accounts are pre-created, so an identifier
 * that is not in {@code user_directory} is rejected rather than auto-registered.
 */
@Service
public class AuthService {

    private final UserDirectoryRepository userDirectoryRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final OtpProvider otpProvider;
    private final String demoPassword;

    public AuthService(
            UserDirectoryRepository userDirectoryRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            OtpProvider otpProvider,
            @Value("${app.auth.demo-password:Password@123}") String demoPassword) {
        this.userDirectoryRepository = userDirectoryRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.otpProvider = otpProvider;
        this.demoPassword = demoPassword;
    }

    /**
     * Phone/email + password. Issues a JWT on success.
     * {@code identifier} may be either the phone number or the email on file.
     *
     * <p>A stored password hash is accepted when present. In this dummy environment the
     * configured demo password is also accepted for every account so seeded personas
     * (most of which have no hash) can sign in.
     */
    public LoginResult loginWithPassword(String identifier, String password) {
        if (identifier == null || identifier.isBlank() || password == null || password.isBlank()) {
            throw new BadRequestException("identifier and password are required");
        }
        UserDirectoryEntry entry = userDirectoryRepository.findByPhoneNumberOrEmail(identifier.trim())
                .orElseThrow(() -> new UnauthorizedException("Invalid phone/email or password"));
        if ("BRANCH_USER".equals(entry.role()) && entry.passwordHash() == null) {
            throw new UnauthorizedException("Set a password before signing in");
        }

        boolean matchesStored = entry.passwordHash() != null
                && passwordEncoder.matches(password, entry.passwordHash());
        boolean matchesDemo = demoPassword != null && !demoPassword.isBlank() && demoPassword.equals(password);
        if (!matchesStored && !matchesDemo) {
            throw new UnauthorizedException("Invalid phone/email or password");
        }

        return buildLoginResult(entry);
    }

    private LoginResult buildLoginResult(UserDirectoryEntry entry) {
        assertAccountCanLogin(entry);

        String token = jwtService.generateAccessToken(
                entry.userId().toString(), entry.phoneNumber(), entry.userCode(), entry.tenantSchema(), entry.role(),
                entry.displayName(),
                entry.clientId() == null ? null : entry.clientId().toString(),
                entry.branchId() == null ? null : entry.branchId().toString());

        return new LoginResult(token, jwtService.getAccessTokenTtlSeconds(), entry);
    }

    /** Sends the dummy code to a branch phone that still has no password. */
    public void requestBranchPasswordCode(String phoneNumber) {
        awaitingPassword(phoneNumber);
        otpProvider.sendOtp(phoneNumber);
    }

    /** Verifies the code and stores the branch user's first password. */
    public void setBranchPassword(String phoneNumber, String otp, String password) {
        if (password == null || password.length() < 6 || password.length() > 72) {
            throw new BadRequestException("Password must be 6 to 72 characters");
        }
        UserDirectoryEntry entry = awaitingPassword(phoneNumber);
        if (!otpProvider.verifyOtp(phoneNumber, otp)) {
            throw new BadRequestException("That code is not valid");
        }
        userDirectoryRepository.updatePasswordHash(entry.userId(), passwordEncoder.encode(password));
    }

    private UserDirectoryEntry awaitingPassword(String phoneNumber) {
        if (phoneNumber == null || !phoneNumber.matches("\\d{10}")) {
            throw new BadRequestException("Enter a 10-digit phone number");
        }
        UserDirectoryEntry entry = userDirectoryRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new BadRequestException("This phone is not waiting to set a password"));
        if (!"BRANCH_USER".equals(entry.role()) || entry.passwordHash() != null) {
            throw new BadRequestException("This phone is not waiting to set a password");
        }
        if (!"ACTIVE".equals(entry.status())) {
            throw new BadRequestException("This account is " + entry.status().toLowerCase() + ". Contact your admin.");
        }
        return entry;
    }

    private void assertAccountCanLogin(UserDirectoryEntry entry) {
        if (!"ACTIVE".equals(entry.status())) {
            throw new UnauthorizedException("This account is " + entry.status().toLowerCase() + ". Contact your admin.");
        }
    }

    public record LoginResult(String accessToken, long expiresInSeconds, UserDirectoryEntry user) {
    }
}
