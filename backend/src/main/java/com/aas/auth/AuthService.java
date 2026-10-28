package com.aas.auth;

import com.aas.auth.AuthDtos.LoginResponse;
import com.aas.auth.AuthDtos.MfaEnrollment;
import com.aas.auth.AuthDtos.UserView;
import com.aas.common.ApiException;
import com.aas.org.OrganizationRepository;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final String BAD_CREDENTIALS = "Invalid email or password";

    private final AppUserRepository users;
    private final OrganizationRepository orgs;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;
    private final TotpService totp;
    private final TokenDenylist denylist;
    private final boolean devOtpEcho;

    public AuthService(AppUserRepository users, OrganizationRepository orgs, PasswordEncoder passwordEncoder,
                       JwtService jwt, TotpService totp, TokenDenylist denylist,
                       @Value("${aas.security.dev-otp-echo:false}") boolean devOtpEcho) {
        this.users = users;
        this.orgs = orgs;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
        this.totp = totp;
        this.denylist = denylist;
        this.devOtpEcho = devOtpEcho;
    }

    /** Step 1: check the password. HR roles then get an MFA challenge instead of a session. */
    @Transactional(noRollbackFor = ApiException.class)
    public LoginResponse login(String email, String password) {
        AppUser user = users.findByEmailIgnoreCase(email.trim())
                .filter(AppUser::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", BAD_CREDENTIALS));
        LocalDateTime now = LocalDateTime.now();
        if (user.isLocked(now)) {
            throw new ApiException(HttpStatus.LOCKED, "ACCOUNT_LOCKED",
                    "Account locked after too many failed attempts. Try again after 15 minutes.");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            registerFailure(user, now);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", BAD_CREDENTIALS);
        }
        user.setFailedAttempts(0);
        user.setLockedUntil(null);

        if (!user.getRole().requiresMfa()) {
            return session(user);
        }
        MfaEnrollment enrollment = null;
        if (!user.isMfaEnabled()) {
            if (user.getMfaSecret() == null) {
                user.setMfaSecret(totp.newSecret());
            }
            enrollment = new MfaEnrollment(user.getMfaSecret(),
                    totp.otpauthUrl("Attrition Analysis", user.getEmail(), user.getMfaSecret()));
        }
        String devOtp = devOtpEcho ? totp.currentCode(user.getMfaSecret()) : null;
        return LoginResponse.challenge(jwt.challengeToken(user), enrollment, devOtp);
    }

    /** Step 2 for HR roles: exchange the challenge token and a valid one-time code for a session. */
    @Transactional(noRollbackFor = ApiException.class)
    public LoginResponse verifyOtp(String challengeToken, String code) {
        Claims claims = jwt.parse(challengeToken, JwtService.TYPE_MFA)
                .filter(c -> !denylist.isRevoked(c.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "CHALLENGE_EXPIRED",
                        "Your sign-in attempt expired. Please sign in again."));
        AppUser user = users.findById(Long.valueOf(claims.getSubject()))
                .filter(AppUser::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "CHALLENGE_EXPIRED", "Please sign in again."));
        LocalDateTime now = LocalDateTime.now();
        if (user.isLocked(now)) {
            throw new ApiException(HttpStatus.LOCKED, "ACCOUNT_LOCKED",
                    "Account locked after too many failed attempts. Try again after 15 minutes.");
        }
        if (!totp.verify(user.getMfaSecret(), code)) {
            registerFailure(user, now);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CODE", "Invalid or expired code");
        }
        denylist.revoke(claims.getId(), claims.getExpiration().toInstant());
        user.setMfaEnabled(true);
        user.setFailedAttempts(0);
        return session(user);
    }

    /** Sliding session: a still-valid token is swapped for a fresh 30-minute one and the old one revoked. */
    public LoginResponse refresh(Claims current) {
        AppUser user = users.findById(Long.valueOf(current.getSubject()))
                .filter(AppUser::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Please sign in again."));
        denylist.revoke(current.getId(), current.getExpiration().toInstant());
        return session(user);
    }

    public void logout(Claims current) {
        denylist.revoke(current.getId(), current.getExpiration().toInstant());
    }

    public UserView view(AppUser user) {
        String orgName = orgs.findById(user.getOrgId()).map(o -> o.getName()).orElse(null);
        return new UserView(user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
                user.getDepartmentId(), user.getOrgId(), orgName);
    }

    public UserView me(Long userId) {
        return users.findById(userId).map(this::view).orElseThrow(() -> ApiException.notFound("User"));
    }

    private LoginResponse session(AppUser user) {
        return LoginResponse.session(jwt.accessToken(user), jwt.accessTtlSeconds(), view(user));
    }

    private void registerFailure(AppUser user, LocalDateTime now) {
        int failures = user.getFailedAttempts() + 1;
        user.setFailedAttempts(failures);
        if (failures >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(now.plus(LOCK_DURATION));
            user.setFailedAttempts(0);
        }
        users.save(user);
    }
}
