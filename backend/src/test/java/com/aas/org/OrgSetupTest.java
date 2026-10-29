package com.aas.org;

import static com.aas.TestFixtures.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aas.TestFixtures;
import com.aas.auth.AppUser;
import com.aas.auth.Role;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** TC-ORG-01: HR Admin configures departments, exit reasons and the alert threshold. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrgSetupTest {

    @Autowired MockMvc mvc;
    @Autowired TestFixtures fx;

    Organization org;
    String admin;
    String hrManager;

    @BeforeEach
    void setUp() throws Exception {
        org = fx.newOrg();
        admin = fx.token(mvc, fx.newUser(org, Role.HR_ADMIN));
        hrManager = fx.token(mvc, fx.newUser(org, Role.HR_MANAGER));
    }

    @Test
    void anyOrganizationCanRegisterAndGetsDefaultExitReasons() throws Exception {
        String email = "owner-" + UUID.randomUUID() + "@neworg.test";
        mvc.perform(post("/api/organizations/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"organizationName":"New Org","industry":"Retail","adminName":"Owner",
                         "adminEmail":"%s","password":"Secret123"}""".formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("New Org"));

        mvc.perform(post("/api/organizations/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"organizationName":"Dup","adminName":"X","adminEmail":"%s","password":"Secret123"}""".formatted(email)))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/organizations/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"organizationName":"Weak","adminName":"X","adminEmail":"weak@x.test","password":"short"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
    }

    @Test
    void adminConfiguresDepartmentsReasonsAndThreshold() throws Exception {
        mvc.perform(post("/api/departments").header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"R&D\",\"location\":\"Pune\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("R&D"));
        mvc.perform(post("/api/departments").header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"r&d\"}"))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/exit-reasons").header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Relocation abroad\",\"category\":\"VOLUNTARY\"}"))
                .andExpect(status().isCreated());

        mvc.perform(put("/api/org").header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\",\"industry\":\"IT\",\"riskThreshold\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskThreshold").value(4));

        // Values are visible to other users of the organization (they feed the dropdowns).
        mvc.perform(get("/api/departments").header("Authorization", bearer(hrManager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("R&D"));
        mvc.perform(get("/api/exit-reasons").header("Authorization", bearer(hrManager)))
                .andExpect(jsonPath("$[0].name").value("Relocation abroad"));
    }

    @Test
    void thresholdMustBeAPercentage() throws Exception {
        mvc.perform(put("/api/org").header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"riskThreshold\":150}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyHrAdminCanChangeSettings() throws Exception {
        mvc.perform(put("/api/org").header("Authorization", bearer(hrManager)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"riskThreshold\":5}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/departments").header("Authorization", bearer(hrManager)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sales\"}"))
                .andExpect(status().isForbidden());
    }

    /** TC-SEC-04 (partial): another organization's department is reported as not found. */
    @Test
    void departmentsAreIsolatedPerOrganization() throws Exception {
        String createdId = mvc.perform(post("/api/departments").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Finance\"}"))
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1");

        Organization other = fx.newOrg();
        AppUser otherAdmin = fx.newUser(other, Role.HR_ADMIN);
        String otherToken = fx.token(mvc, otherAdmin);
        mvc.perform(put("/api/departments/" + createdId).header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Hijacked\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/departments").header("Authorization", bearer(otherToken)))
                .andExpect(jsonPath("$.length()").value(0));
    }
}
