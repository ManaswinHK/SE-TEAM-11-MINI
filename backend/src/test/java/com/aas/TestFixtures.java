package com.aas;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.aas.auth.AppUser;
import com.aas.auth.AppUserRepository;
import com.aas.auth.Role;
import com.aas.auth.TotpService;
import com.aas.org.Organization;
import com.aas.org.OrganizationRepository;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;

/** Creates isolated organizations and users for integration tests and signs them in. */
@Component
public class TestFixtures {

    public static final String PASSWORD = "Test@12345";
    private static final Pattern TOKEN = Pattern.compile("\"(accessToken|challengeToken)\"\\s*:\\s*\"([^\"]+)\"");

    private final OrganizationRepository orgs;
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final TotpService totp;

    public TestFixtures(OrganizationRepository orgs, AppUserRepository users, PasswordEncoder encoder, TotpService totp) {
        this.orgs = orgs;
        this.users = users;
        this.encoder = encoder;
        this.totp = totp;
    }

    public Organization newOrg() {
        return orgs.save(new Organization("Org " + UUID.randomUUID(), "Testing"));
    }

    public AppUser newUser(Organization org, Role role) {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.local";
        return users.save(new AppUser(org.getId(), email, role.name() + " User", encoder.encode(PASSWORD), role));
    }

    public AppUser save(AppUser user) {
        return users.save(user);
    }

    public String currentOtp(AppUser user) {
        return totp.currentCode(users.findById(user.getId()).orElseThrow().getMfaSecret());
    }

    /** Full sign-in (including the OTP step for HR roles); returns the access token. */
    public String token(MockMvc mvc, AppUser user) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", user.getEmail(), "password", PASSWORD)))
                .andReturn().getResponse().getContentAsString();
        Matcher m = TOKEN.matcher(body);
        if (!m.find()) {
            throw new IllegalStateException("Login failed: " + body);
        }
        if (m.group(1).equals("accessToken")) {
            return m.group(2);
        }
        String verified = mvc.perform(post("/api/auth/verify-otp").contentType(MediaType.APPLICATION_JSON)
                        .content(json("challengeToken", m.group(2), "code", currentOtp(user))))
                .andReturn().getResponse().getContentAsString();
        Matcher v = TOKEN.matcher(verified);
        if (!v.find()) {
            throw new IllegalStateException("OTP verification failed: " + verified);
        }
        return v.group(2);
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }

    /** Tiny JSON builder for flat string objects: json("a", "1", "b", "2"). */
    public static String json(String... kv) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < kv.length; i += 2) {
            if (i > 0) sb.append(',');
            sb.append('"').append(kv[i]).append("\":\"").append(kv[i + 1].replace("\"", "\\\"")).append('"');
        }
        return sb.append('}').toString();
    }
}
