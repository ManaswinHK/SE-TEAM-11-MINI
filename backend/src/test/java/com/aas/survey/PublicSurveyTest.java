package com.aas.survey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aas.TestFixtures;
import com.aas.employee.Employee;
import com.aas.employee.EmployeeRepository;
import com.aas.org.Department;
import com.aas.org.DepartmentRepository;
import com.aas.org.ExitReason;
import com.aas.org.ExitReasonRepository;
import com.aas.org.Organization;
import com.aas.separation.Separation;
import com.aas.separation.SeparationRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
class PublicSurveyTest {

    static final String ANSWERS = """
            {"manager":4,"pay":2,"growth":3,"workLife":5,"culture":4,"comments":"  Pay was below market.  "}""";

    @Autowired MockMvc mvc;
    @Autowired TestFixtures fx;
    @Autowired DepartmentRepository departments;
    @Autowired EmployeeRepository employees;
    @Autowired ExitReasonRepository reasons;
    @Autowired SeparationRepository separations;
    @Autowired SurveyInviteRepository invites;
    @Autowired SurveyResponseRepository responses;

    Organization org;
    Separation separation;

    @BeforeEach
    void setUp() {
        org = fx.newOrg();
        Department sales = departments.save(new Department(org.getId(), "Sales", "Mumbai"));
        ExitReason pay = reasons.save(new ExitReason(org.getId(), "Compensation", ExitReason.Category.VOLUNTARY));
        Employee riya = employees.save(new Employee(org.getId(), "E1", "Riya Sharma", sales.getId(), LocalDate.of(2023, 1, 9)));
        separation = separations.save(new Separation(org.getId(), riya.getId(), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                Separation.Type.VOLUNTARY, pay.getId(), false, null, null));
    }

    private String invite(LocalDateTime created) {
        return invites.save(new SurveyInvite(org.getId(), separation.getId(), SurveyService.newToken(), created)).getToken();
    }

    private ResultActions submit(String token, String json) throws Exception {
        return mvc.perform(post("/api/public/survey/" + token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** TC-SRV-02: the first submission is accepted without a login; reusing the link is rejected. */
    @Test
    void submitsOnceWithoutLoggingIn() throws Exception {
        String token = invite(LocalDateTime.now());
        mvc.perform(get("/api/public/survey/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationName").value(org.getName()))
                .andExpect(jsonPath("$.employeeName").doesNotExist());

        submit(token, ANSWERS).andExpect(status().isNoContent());
        assertThat(responses.findByOrgId(org.getId())).singleElement().satisfies(r -> {
            assertThat(r.getSeparationId()).isEqualTo(separation.getId());
            assertThat(r.getPay()).isEqualTo(2);
            assertThat(r.getComments()).isEqualTo("Pay was below market.");
        });

        submit(token, ANSWERS).andExpect(status().isConflict());
        mvc.perform(get("/api/public/survey/" + token)).andExpect(status().isConflict());
        assertThat(responses.findByOrgId(org.getId())).hasSize(1);
    }

    /** TC-SRV-02: a 31-day-old link is rejected; a 29-day-old one still works. */
    @Test
    void linkExpiresAfterThirtyDays() throws Exception {
        String old = invite(LocalDateTime.now().minusDays(31));
        submit(old, ANSWERS).andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("SURVEY_EXPIRED"));
        assertThat(responses.findByOrgId(org.getId())).isEmpty();
    }

    @Test
    void linkStillWorksOnDayTwentyNine() throws Exception {
        submit(invite(LocalDateTime.now().minusDays(29)), ANSWERS).andExpect(status().isNoContent());
    }

    @Test
    void cancelledAndUnknownLinksAreRejected() throws Exception {
        String token = invite(LocalDateTime.now());
        SurveyInvite invite = invites.findByToken(token).orElseThrow();
        invite.cancel(LocalDateTime.now());
        invites.save(invite);

        submit(token, ANSWERS).andExpect(status().isGone());
        submit("not-a-real-token", ANSWERS).andExpect(status().isNotFound());
    }

    @Test
    void ratingsMustBeBetweenOneAndFive() throws Exception {
        String token = invite(LocalDateTime.now());
        submit(token, ANSWERS.replace("\"pay\":2", "\"pay\":6")).andExpect(status().isBadRequest());
        submit(token, ANSWERS.replace("\"manager\":4,", "")).andExpect(status().isBadRequest());
        assertThat(responses.findByOrgId(org.getId())).isEmpty();

        submit(token, ANSWERS).andExpect(status().isNoContent());  // a rejected attempt does not use up the link
    }
}
