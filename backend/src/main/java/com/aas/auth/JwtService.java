package com.aas.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Issues and validates HS256 tokens. Access tokens live 30 minutes and are renewed on activity,
 * so an idle session expires after 30 minutes (AAS-SR-008). MFA challenge tokens live 5 minutes
 * and can only be exchanged at /api/auth/verify-otp.
 */
@Service
public class JwtService {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_MFA = "mfa";

    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration challengeTtl = Duration.ofMinutes(5);

    public JwtService(@Value("${aas.security.jwt-secret}") String secret,
                      @Value("${aas.security.access-token-minutes:30}") long accessMinutes) {
        if (secret.length() < 32) {
            throw new IllegalStateException("aas.security.jwt-secret must be at least 32 characters");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtl = Duration.ofMinutes(accessMinutes);
    }

    public String accessToken(AppUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(user.getId()))
                .claim("typ", TYPE_ACCESS)
                .claim("org", user.getOrgId())
                .claim("email", user.getEmail())
                .claim("name", user.getFullName())
                .claim("role", user.getRole().name())
                .claim("dept", user.getDepartmentId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    public String challengeToken(AppUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(user.getId()))
                .claim("typ", TYPE_MFA)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(challengeTtl)))
                .signWith(key)
                .compact();
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }

    /** Returns the claims if the signature and expiry are valid and the token is of the expected type. */
    public Optional<Claims> parse(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return expectedType.equals(claims.get("typ", String.class)) ? Optional.of(claims) : Optional.empty();
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public CurrentUser toCurrentUser(Claims c) {
        Number dept = c.get("dept", Number.class);
        return new CurrentUser(Long.valueOf(c.getSubject()), c.get("org", Number.class).longValue(),
                c.get("email", String.class), c.get("name", String.class), Role.valueOf(c.get("role", String.class)),
                dept == null ? null : dept.longValue());
    }
}
