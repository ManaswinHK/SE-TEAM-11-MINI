package com.aas.common;

import static com.aas.TestFixtures.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aas.TestFixtures;
import com.aas.auth.AppUser;
import com.aas.auth.Role;
import com.aas.employee.Employee;
import com.aas.employee.EmployeeRepository;
import com.aas.org.Department;
import com.aas.org.DepartmentRepository;
import com.aas.org.ExitReason;
import com.aas.org.ExitReasonRepository;
import com.aas.org.Organization;
import com.aas.separation.Separation;
import com.aas.separation.SeparationRepository;
import com.aas.survey.SurveyResponse;
import com.aas.survey.SurveyResponseRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityHardeningTest {

    @Autowired MockMvc mvc;
    @Autowired TestFixtures fx;
    @Autowired DepartmentRepository departments;
    @Autowired EmployeeRepository employees;
    @Autowired ExitReasonRepository reasons;
    @Autowired SeparationRepository separations;
    @Autowired SurveyResponseRepository responses;
    @Autowired AuditLogRepository audit;
    @Autowired JdbcTemplate jdbc;

    Organization org;
    Department sales;
    Employee riya;
    Separation exit;
    AppUser admin;
    String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        org = fx.newOrg();
        sales = departments.save(new Department(org.getId(), "Sales", "Mumbai"));
        ExitReason pay = reasons.save(new ExitReason(org.getId(), "Compensation", ExitReason.Category.VOLUNTARY));
        Employee e = new Employee(org.getId(), "E1", "Riya Sharma", sales.getId(), LocalDate.of(2023, 1, 9));
        e.setEmail("riya.sharma@example.com");
        e.setGender("Female");
        riya = employees.save(e);
        Employee gone = employees.save(new Employee(org.getId(), "E2", "Karan Rao", sales.getId(), LocalDate.of(2022, 1, 1)));
        exit = separations.save(new Separation(org.getId(), gone.getId(), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                Separation.Type.VOLUNTARY, pay.getId(), false, null, null));
        admin = fx.newUser(org, Role.HR_ADMIN);
        adminToken = fx.token(mvc, admin);
    }

    /** TC-SEC-03: personal data and survey free text are ciphertext in the database but readable through the API. */
    @Test
    void personalDataIsEncryptedAtRest() throws Exception {
        responses.save(new SurveyResponse(org.getId(), exit.getId(), 3, 2, 3, 4, 3, "My manager never listened", LocalDateTime.now()));

        String email = jdbc.queryForObject("select email from employee where id = ?", String.class, riya.getId());
        String gender = jdbc.queryForObject("select gender from employee where id = ?", String.class, riya.getId());
        String comments = jdbc.queryForObject("select comments from survey_response where separation_id = ?", String.class, exit.getId());
        assertThat(email).startsWith("enc:").doesNotContain("riya");
        assertThat(gender).startsWith("enc:").doesNotContain("Female");
        assertThat(comments).startsWith("enc:").doesNotContain("manager");

        mvc.perform(get("/api/employees/" + riya.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.email").value("riya.sharma@example.com"))
                .andExpect(jsonPath("$.gender").value("Female"));
        assertThat(responses.findByOrgId(org.getId())).singleElement()
                .satisfies(r -> assertThat(r.getComments()).isEqualTo("My manager never listened"));
    }

    @Test
    void valuesStoredBeforeEncryptionAreStillReadable() {
        jdbc.update("update employee set email = 'legacy@example.com' where id = ?", riya.getId());
        assertThat(employees.findById(riya.getId()).orElseThrow().getEmail()).isEqualTo("legacy@example.com");
    }

    /** TC-SEC-04: another organization's HR Admin gets 404 for every record of ours and sees none in lists. */
    @Test
    void anotherTenantCannotReachOurRecords() throws Exception {
        String outsider = fx.token(mvc, fx.newUser(fx.newOrg(), Role.HR_ADMIN));
        long emp = riya.getId();
        long sep = exit.getId();
        MockHttpServletRequestBuilder[] attempts = {
                get("/api/employees/" + emp),
                put("/api/employees/" + emp).contentType(MediaType.APPLICATION_JSON).content(
                        "{\"employeeCode\":\"E1\",\"fullName\":\"X\",\"departmentId\":" + sales.getId() + ",\"joinDate\":\"2023-01-09\"}"),
                post("/api/employees/" + emp + "/deactivate"),
                get("/api/separations/" + sep),
                get("/api/separations/" + sep + "/clearance"),
                post("/api/separations/" + sep + "/withdraw"),
                post("/api/separations/" + sep + "/close"),
                post("/api/retention-actions").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"employeeId\":" + emp + ",\"action\":\"x\",\"ownerName\":\"x\",\"dueDate\":\"2099-01-01\"}"),
                put("/api/departments/" + sales.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hacked\",\"location\":\"x\"}"),
        };
        for (MockHttpServletRequestBuilder attempt : attempts) {
            int code = mvc.perform(attempt.header("Authorization", bearer(outsider))).andReturn().getResponse().getStatus();
            assertThat(code).as(attempt.buildRequest(new org.springframework.mock.web.MockServletContext()).getRequestURI())
                    .isEqualTo(404);
        }
        mvc.perform(get("/api/employees").header("Authorization", bearer(outsider)))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/separations?from=2026-01-01&to=2026-12-31").header("Authorization", bearer(outsider)))
                .andExpect(jsonPath("$.length()").value(0));
        assertThat(employees.findById(emp).orElseThrow().getFullName()).isEqualTo("Riya Sharma");
    }

    /** TC-SEC-05: employee and role changes are audited, and no endpoint can change the audit log. */
    @Test
    void changesAreAuditedAndTheLogCannotBeEdited() throws Exception {
        mvc.perform(put("/api/employees/" + riya.getId()).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeCode\":\"E1\",\"fullName\":\"Riya Sharma\",\"departmentId\":" + sales.getId()
                                + ",\"joinDate\":\"2023-01-09\",\"designation\":\"Lead\"}"))
                .andExpect(status().isOk());
        AppUser manager = fx.newUser(org, Role.HR_MANAGER);
        mvc.perform(put("/api/users/" + manager.getId()).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Promoted\",\"role\":\"HR_ADMIN\"}"))
                .andExpect(status().isOk());

        assertThat(audit.findByOrgIdAndEntityAndEntityIdOrderByCreatedAt(org.getId(), "EMPLOYEE", riya.getId()))
                .extracting(AuditLog::getAction).containsExactly("UPDATE");
        assertThat(audit.findByOrgIdAndEntityAndEntityIdOrderByCreatedAt(org.getId(), "USER", manager.getId()))
                .singleElement().satisfies(a -> assertThat(a.getDetails()).contains("HR_ADMIN"));

        mvc.perform(get("/api/audit-logs").header("Authorization", bearer(adminToken))).andExpect(status().isNotFound());
        mvc.perform(put("/api/audit-logs/1").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
    }

    @Test
    void badParametersAreClientErrors() throws Exception {
        mvc.perform(get("/api/risk?band=EXTREME").header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/employees/abc").header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest());
    }
}
