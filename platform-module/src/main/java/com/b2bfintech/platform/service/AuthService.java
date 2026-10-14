package com.b2bfintech.platform.service;

import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.UnauthorizedException;
import com.b2bfintech.common.security.JwtService;
import com.b2bfintech.platform.model.UserDirectoryEntry;
import com.b2bfintech.platform.repository.OrganisationRepository;
import com.b2bfintech.platform.repository.UserDirectoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Password login shared by all three modules. Organisation/client users are pre-created by an
 * admin, so an identifier that is not in {@code platform.user_directory} is rejected rather
 * than auto-registered.
 */
@Service
public class AuthService {

    private final UserDirectoryRepository userDirectoryRepository;
    private final OrganisationRepository organisationRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final String demoPassword;

    public AuthService(
            UserDirectoryRepository userDirectoryRepository,
            OrganisationRepository organisationRepository, JwtService jwtService,
            PasswordEncoder passwordEncoder,
            @Value("${app.auth.demo-password:Password@123}") String demoPassword) {
        this.userDirectoryRepository = userDirectoryRepository;
        this.organisationRepository = organisationRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
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
                entry.userId().toString(), entry.phoneNumber(), entry.tenantSchema(), entry.role(), entry.displayName(),
                entry.clientId() == null ? null : entry.clientId().toString());

        return new LoginResult(token, jwtService.getAccessTokenTtlSeconds(), entry);
    }

    private void assertAccountCanLogin(UserDirectoryEntry entry) {
        if (!"ACTIVE".equals(entry.status())) {
            throw new UnauthorizedException("This account is " + entry.status().toLowerCase() + ". Contact your admin.");
        }
        if (entry.tenantSchema() != null) {
            var organisation = organisationRepository.findByTenantSchema(entry.tenantSchema())
                    .orElseThrow(() -> new UnauthorizedException("This organisation is no longer on the platform."));
            if (!"ACTIVE".equals(organisation.status())) {
                throw new UnauthorizedException("This organisation is suspended. Contact the platform.");
            }
        }
    }

    public record LoginResult(String accessToken, long expiresInSeconds, UserDirectoryEntry user) {
    }
}
