package com.aas.hrms;

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
import com.aas.hrms.HrmsClient.HrmsRecord;
import com.aas.org.Department;
import com.aas.org.DepartmentRepository;
import com.aas.org.Organization;
import com.aas.report.MonthlyReportService;
import com.aas.survey.RecordingMailGateway;
import java.time.LocalDate;
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
class HrmsSyncAndReportTest {

    @Autowired MockMvc mvc;
    @Autowired TestFixtures fx;
    @Autowired DepartmentRepository departments;
    @Autowired EmployeeRepository employees;
    @Autowired StubHrmsClient hrms;
    @Autowired HrmsSyncService sync;
    @Autowired MonthlyReportService reports;
    @Autowired RecordingMailGateway mail;

    Organization org;
    Department sales;
    Department finance;
    Employee riya;
    AppUser admin;

    @BeforeEach
    void setUp() {
        org = fx.newOrg();
        sales = departments.save(new Department(org.getId(), "Sales", "Mumbai"));
        finance = departments.save(new Department(org.getId(), "Finance", "Pune"));
        riya = employees.save(new Employee(org.getId(), "E1001", "Riya Sharma", sales.getId(), LocalDate.of(2023, 1, 9)));
        admin = fx.newUser(org, Role.HR_ADMIN);
    }

    /** TC-EMP-03: a change in the HRMS sandbox is applied by the sync job, which logs 'success'. */
    @Test
    void syncAppliesHrmsChangesAndLogsSuccess() throws Exception {
        hrms.put(org.getId(), new HrmsRecord("E1001", "Riya Sharma", "riya@x.test", "Finance", "Senior Analyst",
                "Arjun Nair", "Pune", "L3", LocalDate.of(2023, 1, 9)));
        hrms.put(org.getId(), new HrmsRecord("E2002", "Karan Rao", null, "Sales", "Sales Executive", null, "Mumbai",
                "L1", LocalDate.of(2026, 10, 1)));
        hrms.put(org.getId(), new HrmsRecord("E3003", "Nina", null, "Legal", null, null, null, null, LocalDate.of(2026, 1, 1)));

        String token = fx.token(mvc, admin);
        mvc.perform(post("/api/hrms/sync").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.updated").value(1))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.message").value("E3003: unknown department 'Legal'"));

        Employee after = employees.findById(riya.getId()).orElseThrow();
        assertThat(after.getDepartmentId()).isEqualTo(finance.getId());
        assertThat(after.getDesignation()).isEqualTo("Senior Analyst");
        assertThat(employees.findByOrgId(org.getId())).extracting(Employee::getEmployeeCode).contains("E2002");

        // Nothing changed since, so a second run updates nothing.
        assertThat(sync.sync(org.getId()).getUpdated()).isZero();
        mvc.perform(get("/api/hrms/sync-log").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void failedSyncChangesNothingAndAlertsTheAdmins() {
        hrms.put(org.getId(), new HrmsRecord("E1001", "Riya S", null, "Finance", null, null, null, null, null));
        hrms.failNextFetch(new IllegalStateException("HRMS returned 503"));

        HrmsSyncLog run = sync.sync(org.getId());
        assertThat(run.getStatus()).isEqualTo(HrmsSyncLog.Status.FAILED);
        assertThat(run.getMessage()).isEqualTo("HRMS returned 503");
        assertThat(employees.findById(riya.getId()).orElseThrow().getDepartmentId()).isEqualTo(sales.getId());
        assertThat(mail.to(admin.getEmail())).singleElement()
                .satisfies(m -> assertThat(m.subject()).isEqualTo("HRMS sync failed"));
    }

    /** TC-REP-03: a subscribed user gets last month's summary on the 2nd, and only then. */
    @Test
    void subscribersGetTheMonthlyReportOnTheSecond() throws Exception {
        AppUser ceo = fx.newUser(org, Role.LEADERSHIP);
        AppUser hr = fx.newUser(org, Role.HR_MANAGER);  // not subscribed
        String token = fx.token(mvc, ceo);
        mvc.perform(put("/api/reports/subscription").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"subscribed\":true}"))
                .andExpect(jsonPath("$.subscribed").value(true));

        LocalDate third = LocalDate.of(2026, 11, 3);
        reports.sendMonthlyReports(third);
        assertThat(mail.to(ceo.getEmail())).isEmpty();

        reports.sendMonthlyReports(LocalDate.of(2026, 11, 2));
        assertThat(mail.to(ceo.getEmail())).singleElement().satisfies(m -> {
            assertThat(m.subject()).isEqualTo(org.getName() + " attrition report, October 2026");
            assertThat(m.body()).contains("2026-10-01 to 2026-10-31").contains("Sales");
        });
        assertThat(mail.to(hr.getEmail())).isEmpty();
    }

    @Test
    void onlyHrAdminsCanSync() throws Exception {
        String hr = fx.token(mvc, fx.newUser(org, Role.HR_MANAGER));
        mvc.perform(post("/api/hrms/sync").header("Authorization", bearer(hr))).andExpect(status().isForbidden());
    }
}
