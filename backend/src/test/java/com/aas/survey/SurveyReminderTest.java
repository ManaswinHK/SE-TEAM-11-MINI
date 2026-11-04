package com.aas.survey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.time.temporal.ChronoUnit;
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
class SurveyReminderTest {

    @Autowired MockMvc mvc;
    @Autowired TestFixtures fx;
    @Autowired DepartmentRepository departments;
    @Autowired EmployeeRepository employees;
    @Autowired ExitReasonRepository reasons;
    @Autowired SeparationRepository separations;
    @Autowired SurveyInviteRepository invites;
    @Autowired SurveyService service;
    @Autowired RecordingMailGateway mail;

    String address;
    SurveyInvite invite;
    LocalDateTime start;

    /** One survey, sent now, to an address no other test uses. */
    @BeforeEach
    void setUp() {
        Organization org = fx.newOrg();
        Department sales = departments.save(new Department(org.getId(), "Sales", "Mumbai"));
        ExitReason pay = reasons.save(new ExitReason(org.getId(), "Compensation", ExitReason.Category.VOLUNTARY));
        Employee e = new Employee(org.getId(), "E1", "Riya Sharma", sales.getId(), LocalDate.of(2023, 1, 9));
        address = "riya-" + UUID.randomUUID() + "@x.test";
        e.setEmail(address);
        employees.save(e);
        Separation s = separations.save(new Separation(org.getId(), e.getId(), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                Separation.Type.VOLUNTARY, pay.getId(), false, null, null));
        start = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);  // the database rounds to microseconds
        SurveyInvite created = new SurveyInvite(org.getId(), s.getId(), SurveyService.newToken(), start);
        created.markSent(start);
        invite = invites.save(created);
    }

    private void runDailyFor(int days) {
        for (int day = 1; day <= days; day++) {
            service.sendReminders(start.plusDays(day));
        }
    }

    /** TC-SRV-03: left unanswered for 9 days, exactly 2 reminders go out, on days 3 and 6. */
    @Test
    void exactlyTwoRemindersForANeverSubmittedSurvey() {
        runDailyFor(2);
        assertThat(mail.to(address)).isEmpty();

        runDailyFor(3);
        assertThat(mail.to(address)).hasSize(1);

        runDailyFor(9);
        assertThat(mail.to(address)).hasSize(2)
                .allSatisfy(m -> assertThat(m.body()).contains("/survey/" + invite.getToken()));
        assertThat(invites.findById(invite.getId()).orElseThrow().getRemindersSent()).isEqualTo(2);
    }

    /** TC-SRV-03: no reminder after the survey has been answered. */
    @Test
    void noReminderAfterSubmission() throws Exception {
        mvc.perform(post("/api/public/survey/" + invite.getToken()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"manager":4,"pay":2,"growth":3,"workLife":5,"culture":4}"""))
                .andExpect(status().isNoContent());

        runDailyFor(9);
        assertThat(mail.to(address)).isEmpty();
    }

    @Test
    void noReminderOnceTheLinkHasExpired() {
        service.sendReminders(start.plusDays(SurveyInvite.VALID_DAYS + 1));
        assertThat(mail.to(address)).isEmpty();
    }
}
