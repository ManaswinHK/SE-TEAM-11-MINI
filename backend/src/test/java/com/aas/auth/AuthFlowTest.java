package com.aas.auth;

import static com.aas.TestFixtures.PASSWORD;
import static com.aas.TestFixtures.bearer;
import static com.aas.TestFixtures.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aas.TestFixtures;
import com.aas.org.Organization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowTest {

    @Autowired MockMvc mvc;
    @Autowired TestFixtures fx;
    @Autowired AppUserRepository users;

    Organization org;

    @BeforeEach
    void setUp() {
        org = fx.newOrg();
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json("email", email, "password", password)));
    }

    /** TC-AUTH-01: HR roles need a valid one-time code; a wrong code gives no session. */
    @Test
    void hrManagerMustCompleteOtpStep() throws Exception {
        AppUser hr = fx.newUser(org, Role.HR_MANAGER);

        String body = login(hr.getEmail(), PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired").value(true))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.enrollment.secret").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String challenge = body.replaceAll(".*\"challengeToken\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mvc.perform(post("/api/auth/verify-otp").contentType(MediaType.APPLICATION_JSON)
                        .content(json("challengeToken", challenge, "code", "000000")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CODE"));

        mvc.perform(post("/api/auth/verify-otp").contentType(MediaType.APPLICATION_JSON)
                        .content(json("challengeToken", challenge, "code", fx.currentOtp(hr))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("HR_MANAGER"))
                .andExpect(jsonPath("$.expiresIn").value(1800));

        assertThat(users.findById(hr.getId()).orElseThrow().isMfaEnabled()).isTrue();
    }

    @Test
    void leadershipSignsInWithPasswordOnly() throws Exception {
        AppUser ceo = fx.newUser(org, Role.LEADERSHIP);
        login(ceo.getEmail(), PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired").value(false))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void wrongPasswordAndUnknownUserGiveSameGenericError() throws Exception {
        AppUser ceo = fx.newUser(org, Role.LEADERSHIP);
        login(ceo.getEmail(), "wrong").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
        login("nobody@test.local", "wrong").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    /** TC-SEC-02: bcrypt hashes and lock for 15 minutes after 5 failed attempts. */
    @Test
    void passwordsAreBcryptAndAccountLocksAfterFiveFailures() throws Exception {
        AppUser ceo = fx.newUser(org, Role.LEADERSHIP);
        assertThat(ceo.getPasswordHash()).startsWith("$2a$12$");

        for (int i = 0; i < 5; i++) {
            login(ceo.getEmail(), "wrong-" + i).andExpect(status().isUnauthorized());
        }
        login(ceo.getEmail(), PASSWORD)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        assertThat(users.findById(ceo.getId()).orElseThrow().getLockedUntil()).isNotNull();
    }

    /** TC-SEC-09: a token cannot be used after logout, and missing tokens get 401. */
    @Test
    void logoutRevokesTokenAndRefreshIssuesNewOne() throws Exception {
        AppUser ceo = fx.newUser(org, Role.LEADERSHIP);
        String token = fx.token(mvc, ceo);

        mvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ceo.getEmail()));

        String refreshed = mvc.perform(post("/api/auth/refresh").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()
                .replaceAll(".*\"accessToken\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(token))).andExpect(status().isUnauthorized());

        mvc.perform(post("/api/auth/logout").header("Authorization", bearer(refreshed))).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(refreshed))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    /** TC-SEC-01 (application side): HSTS is sent on HTTPS responses. */
    @Test
    void hstsHeaderSentOverHttps() throws Exception {
        mvc.perform(get("/api/health").secure(true))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"));
    }
}
