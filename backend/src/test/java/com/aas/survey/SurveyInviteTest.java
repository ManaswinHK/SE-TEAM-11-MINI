package com.aas.survey;

import static com.aas.TestFixtures.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aas.TestFixtures;
import com.aas.auth.Role;
import com.aas.employee.Employee;
import com.aas.employee.EmployeeRepository;
import com.aas.org.Department;
import com.aas.org.DepartmentRepository;
import com.aas.org.ExitReason;
import com.aas.org.ExitReasonRepository;
import com.aas.org.Organization;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SurveyInviteTest {

    @Autowired MockMvc mvc;
    @Autowired TestFixtures fx;
    @Autowired DepartmentRepository departments;
    @Autowired EmployeeRepository employees;
    @Autowired ExitReasonRepository reasons;
    @Autowired SurveyInviteRepository invites;
    @Autowired SurveyService service;
    @Autowired RecordingMailGateway mail;

    Organization org;
    Department sales;
    ExitReason pay;
    String hr;

    @BeforeEach
    void setUp() throws Exception {
        org = fx.newOrg();
        sales = departments.save(new Department(org.getId(), "Sales", "Mumbai"));
        pay = reasons.save(new ExitReason(org.getId(), "Compensation", ExitReason.Category.VOLUNTARY));
        hr = fx.token(mvc, fx.newUser(org, Role.HR_MANAGER));
    }

    private Employee employee(String email) {
        Employee e = new Employee(org.getId(), "E" + UUID.randomUUID().toString().substring(0, 8), "Riya Sharma",
                sales.getId(), LocalDate.of(2023, 1, 9));
        e.setEmail(email);
        return employees.save(e);
    }

    private long resign(Employee e) throws Exception {
        String body = mvc.perform(post("/api/separations").header("Authorization", bearer(hr)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employeeId":%d,"resignationDate":"%s","lastWorkingDay":"%s","type":"VOLUNTARY","reasonId":%d,"regretted":false}"""
                                .formatted(e.getId(), LocalDate.now().minusDays(1), LocalDate.now().plusDays(30), pay.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*?\"id\":(\\d+).*", "$1"));
    }

    /** TC-SRV-01: recording a separation sends exactly one email with a tokenised link and no personal data in it. */
    @Test
    void recordingASeparationEmailsOneTokenisedLink() throws Exception {
        String address = "riya-" + UUID.randomUUID() + "@x.test";
        long id = resign(employee(address));
        assertThat(mail.to(address)).isEmpty();  // the job sends it, not the request

        service.sendPending();
        service.sendPending();

        String token = invites.findBySeparationId(id).orElseThrow().getToken();
        assertThat(token).hasSize(43);
        assertThat(mail.to(address)).singleElement()
                .satisfies(m -> assertThat(m.body()).contains("/survey/" + token));
        mvc.perform(get("/api/surveys/invites").header("Authorization", bearer(hr)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].separationId").value(id))
                .andExpect(jsonPath("$[0].status").value("SENT"))
                .andExpect(jsonPath("$[0].token").doesNotExist());
    }

    @Test
    void noInviteWithoutAnEmailAddress() throws Exception {
        long id = resign(employee(null));
        assertThat(invites.findBySeparationId(id)).isEmpty();
    }

    @Test
    void withdrawingCancelsAnUnsentInvite() throws Exception {
        String address = "nina-" + UUID.randomUUID() + "@x.test";
        long id = resign(employee(address));
        mvc.perform(post("/api/separations/" + id + "/withdraw").header("Authorization", bearer(hr)))
                .andExpect(status().isOk());

        service.sendPending();
        assertThat(mail.to(address)).isEmpty();
    }

    @Test
    void aMailFailureIsRetriedOnTheNextRun() throws Exception {
        String address = "arjun-" + UUID.randomUUID() + "@x.test";
        long id = resign(employee(address));

        mail.failNextSend();
        service.sendPending();
        assertThat(mail.to(address)).isEmpty();
        assertThat(invites.findBySeparationId(id).orElseThrow().getSentAt()).isNull();

        service.sendPending();
        assertThat(mail.to(address)).hasSize(1);
    }

    @Test
    void otherRolesCannotListInvites() throws Exception {
        String ceo = fx.token(mvc, fx.newUser(org, Role.LEADERSHIP));
        mvc.perform(get("/api/surveys/invites").header("Authorization", bearer(ceo)))
                .andExpect(status().isForbidden());
    }
}
