package com.aas.analytics;

import static com.aas.TestFixtures.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aas.TestFixtures;
import com.aas.auth.Role;
import com.aas.employee.Employee;
import com.aas.employee.EmployeeRepository;
import com.aas.employee.EmployeeStatus;
import com.aas.org.Department;
import com.aas.org.DepartmentRepository;
import com.aas.org.ExitReason;
import com.aas.org.ExitReasonRepository;
import com.aas.org.Organization;
import com.aas.separation.Separation;
import com.aas.separation.SeparationRepository;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardExportTest {

    static final String PERIOD = "from=2026-01-01&to=2026-06-30";

    @Autowired MockMvc mvc;
    @Autowired TestFixtures fx;
    @Autowired DepartmentRepository departments;
    @Autowired EmployeeRepository employees;
    @Autowired ExitReasonRepository reasons;
    @Autowired SeparationRepository separations;

    String hr;

    @BeforeEach
    void setUp() throws Exception {
        Organization org = fx.newOrg();
        Department sales = departments.save(new Department(org.getId(), "Sales", "Mumbai"));
        ExitReason pay = reasons.save(new ExitReason(org.getId(), "=Compensation, base", ExitReason.Category.VOLUNTARY));
        for (int i = 0; i < 40; i++) {
            Employee e = employees.save(new Employee(org.getId(), "E" + i, "Person " + i, sales.getId(), LocalDate.of(2022, 1, 1)));
            if (i < 3) {
                separations.save(new Separation(org.getId(), e.getId(), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                        Separation.Type.VOLUNTARY, pay.getId(), false, null, null));
                e.setLastWorkingDay(LocalDate.of(2026, 3, 31));
                e.setStatus(EmployeeStatus.SEPARATED);
                employees.save(e);
            }
        }
        hr = fx.token(mvc, fx.newUser(org, Role.HR_MANAGER));
    }

    private static String json(String body, String field) {
        Matcher m = Pattern.compile("\"" + field + "\":([0-9.]+)").matcher(body);
        assertThat(m.find()).as(field).isTrue();
        return m.group(1);
    }

    /** TC-REP-02: the exported CSV carries the same figures as the dashboard for the same filters. */
    @Test
    void csvMatchesTheDashboardFigures() throws Exception {
        String dashboard = mvc.perform(get("/api/analytics/attrition?" + PERIOD).header("Authorization", bearer(hr)))
                .andReturn().getResponse().getContentAsString();
        String csv = mvc.perform(get("/api/analytics/export.csv?" + PERIOD).header("Authorization", bearer(hr)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"attrition-2026-01-01-to-2026-06-30.csv\""))
                .andReturn().getResponse().getContentAsString();

        assertThat(csv).contains("KPI,Attrition rate %," + json(dashboard, "attritionRate") + "\r\n");
        assertThat(csv).contains("KPI,Separations," + json(dashboard, "separations") + "\r\n");
        assertThat(csv).contains("KPI,Average headcount," + json(dashboard, "averageHeadcount") + "\r\n");
        assertThat(csv).contains("Sales,3,38.50,7.79\r\n");
        // A reason starting with '=' is defused and the comma inside it quoted.
        assertThat(csv).contains("\"'=Compensation, base\",3,100.00\r\n");
    }

    @Test
    void exportNeedsSignIn() throws Exception {
        mvc.perform(get("/api/analytics/export.csv?" + PERIOD)).andExpect(status().isUnauthorized());
    }
}
