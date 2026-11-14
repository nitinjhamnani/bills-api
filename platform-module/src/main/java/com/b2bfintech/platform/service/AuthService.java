package com.b2bfintech.platform.service;

import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.UnauthorizedException;
import com.b2bfintech.common.security.JwtService;
import com.b2bfintech.common.spi.OtpProvider;
import com.b2bfintech.organisation.entity.AppUser;
import com.b2bfintech.organisation.entity.UserTypes;
import com.b2bfintech.organisation.repository.AppUserRepository;
import com.b2bfintech.organisation.repository.BranchMembershipRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/** Password login. The account must already exist on {@code app_user}. */
@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final BranchMembershipRepository membershipRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final OtpProvider otpProvider;
    private final String demoPassword;

    public AuthService(
            AppUserRepository appUserRepository,
            BranchMembershipRepository membershipRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            OtpProvider otpProvider,
            @Value("${app.auth.demo-password:Password@123}") String demoPassword) {
        this.appUserRepository = appUserRepository;
        this.membershipRepository = membershipRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.otpProvider = otpProvider;
        this.demoPassword = demoPassword;
    }

    public LoginResult loginWithPassword(String identifier, String password) {
        if (identifier == null || identifier.isBlank() || password == null || password.isBlank()) {
            throw new BadRequestException("identifier and password are required");
        }
        AppUser user = appUserRepository.findByPhoneNumberOrEmail(identifier.trim())
                .orElseThrow(() -> new UnauthorizedException("Invalid phone/email or password"));
        if (branchOnly(user) && (user.getPassword() == null || user.getPassword().isBlank())) {
            throw new UnauthorizedException("Set a password before signing in");
        }

        boolean matchesStored = user.getPassword() != null
                && !user.getPassword().isBlank()
                && passwordEncoder.matches(password, user.getPassword());
        boolean matchesDemo = demoPassword != null && !demoPassword.isBlank() && demoPassword.equals(password);
        if (!matchesStored && !matchesDemo) {
            throw new UnauthorizedException("Invalid phone/email or password");
        }

        return buildLoginResult(user);
    }

    private LoginResult buildLoginResult(AppUser user) {
        if (!user.isActive()) {
            throw new UnauthorizedException("This account is suspended. Contact your admin.");
        }
        List<String> branches = membershipRepository.findBranchIdsByUserId(user.getUserId());
        String branchId = branches.isEmpty() ? null : branches.get(0);
        String role = tokenRole(user, branches);
        String clientId = user.isPaytrix() ? null : user.getUserOrgId();
        String token = jwtService.generateAccessToken(
                user.getUserId(), user.getPhone(), user.getUserId(), null, role,
                user.getName(), clientId, branchId);
        return new LoginResult(token, jwtService.getAccessTokenTtlSeconds(), user, role, clientId, branchId);
    }

    /** A corporate user who belongs to a branch keeps the branch sign-in, which cannot pay. */
    private static String tokenRole(AppUser user, List<String> branches) {
        if (user.hasUserType(AppUser.UserType.CORP_USER) && !branches.isEmpty()) {
            return "BRANCH_USER";
        }
        return UserTypes.legacyRole(user.getUserType());
    }

    private static boolean branchOnly(AppUser user) {
        return user.hasUserType(AppUser.UserType.CORP_USER);
    }

    public void requestBranchPasswordCode(String phoneNumber) {
        awaitingPassword(phoneNumber);
        otpProvider.sendOtp(phoneNumber);
    }

    public void setBranchPassword(String phoneNumber, String otp, String password) {
        if (password == null || password.length() < 6 || password.length() > 72) {
            throw new BadRequestException("Password must be 6 to 72 characters");
        }
        AppUser user = awaitingPassword(phoneNumber);
        if (!otpProvider.verifyOtp(phoneNumber, otp)) {
            throw new BadRequestException("That code is not valid");
        }
        user.setPassword(passwordEncoder.encode(password));
        appUserRepository.save(user);
    }

    private AppUser awaitingPassword(String phoneNumber) {
        if (phoneNumber == null || !phoneNumber.matches("\\d{10}")) {
            throw new BadRequestException("Enter a 10-digit phone number");
        }
        AppUser user = appUserRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new BadRequestException("This phone is not waiting to set a password"));
        if (!branchOnly(user) || (user.getPassword() != null && !user.getPassword().isBlank())) {
            throw new BadRequestException("This phone is not waiting to set a password");
        }
        if (!user.isActive()) {
            throw new BadRequestException("This account is suspended. Contact your admin.");
        }
        return user;
    }

    public record LoginResult(
            String accessToken,
            long expiresInSeconds,
            AppUser user,
            String role,
            String clientId,
            String branchId) {
    }
}
