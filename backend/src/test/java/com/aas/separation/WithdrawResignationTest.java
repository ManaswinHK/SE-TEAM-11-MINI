package com.aas.separation;

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
import com.aas.employee.EmployeeStatus;
import com.aas.org.Department;
import com.aas.org.DepartmentRepository;
import com.aas.org.ExitReason;
import com.aas.org.ExitReasonRepository;
import com.aas.org.Organization;
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
class WithdrawResignationTest {

    @Autowired MockMvc mvc;
    @Autowired TestFixtures fx;
    @Autowired DepartmentRepository departments;
    @Autowired EmployeeRepository employees;
    @Autowired ExitReasonRepository reasons;
    @Autowired SeparationService service;
    @Autowired SeparationRepository separations;

    Organization org;
    Department sales;
    ExitReason compensation;
    String hr;

    @BeforeEach
    void setUp() throws Exception {
        org = fx.newOrg();
        sales = departments.save(new Department(org.getId(), "Sales", "Mumbai"));
        compensation = reasons.save(new ExitReason(org.getId(), "Compensation", ExitReason.Category.VOLUNTARY));
        hr = fx.token(mvc, fx.newUser(org, Role.HR_MANAGER));
        for (int i = 0; i < 10; i++) {
            employees.save(new Employee(org.getId(), "S" + i, "Staff " + i, sales.getId(), LocalDate.of(2022, 1, 1)));
        }
    }

    private long recordExit(Employee e, LocalDate resigned, LocalDate lastDay) throws Exception {
        String body = mvc.perform(post("/api/separations").header("Authorization", bearer(hr)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employeeId":%d,"resignationDate":"%s","lastWorkingDay":"%s","type":"VOLUNTARY","reasonId":%d,"regretted":false}"""
                                .formatted(e.getId(), resigned, lastDay, compensation.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*?\"id\":(\\d+).*", "$1"));
    }

    private String attrition(String period) throws Exception {
        return mvc.perform(get("/api/analytics/attrition?" + period).header("Authorization", bearer(hr)))
                .andReturn().getResponse().getContentAsString();
    }

    /** TC-SEP-04: a withdrawn resignation shows as retained and the attrition rate does not change. */
    @Test
    void withdrawingKeepsTheEmployeeAndLeavesAttritionUnchanged() throws Exception {
        Employee riya = employees.save(new Employee(org.getId(), "E1", "Riya Sharma", sales.getId(), LocalDate.of(2023, 1, 9)));
        LocalDate lastDay = LocalDate.now().plusDays(25);
        String period = "from=" + LocalDate.now().minusMonths(1) + "&to=" + lastDay.plusDays(10);
        String before = attrition(period);

        long id = recordExit(riya, LocalDate.now().minusDays(5), lastDay);
        mvc.perform(post("/api/separations/" + id + "/withdraw").header("Authorization", bearer(hr))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Counter-offer accepted\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.remarks").value("Withdrawn: Counter-offer accepted"));

        Employee after = employees.findById(riya.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(EmployeeStatus.ACTIVE);
        assertThat(after.getLastWorkingDay()).isNull();

        // Even after the original last working day passes, the withdrawn exit is never counted.
        service.completeDueSeparations(lastDay.plusDays(1));
        assertThat(attrition(period)).isEqualTo(before);
        assertThat(employees.findById(riya.getId()).orElseThrow().getStatus()).isEqualTo(EmployeeStatus.ACTIVE);

        // Clearance does not apply, and the person can resign again later.
        mvc.perform(post("/api/separations/" + id + "/close").header("Authorization", bearer(hr)))
                .andExpect(status().isConflict());
        recordExit(riya, LocalDate.now(), LocalDate.now().plusDays(30));
    }

    @Test
    void analyticsIgnoresWithdrawnSeparations() throws Exception {
        Employee a = employees.save(new Employee(org.getId(), "A1", "Left", sales.getId(), LocalDate.of(2022, 1, 1)));
        Employee b = employees.save(new Employee(org.getId(), "B1", "Stayed", sales.getId(), LocalDate.of(2022, 1, 1)));
        separations.save(new Separation(org.getId(), a.getId(), LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31),
                Separation.Type.VOLUNTARY, compensation.getId(), false, null, null));
        Separation withdrawn = new Separation(org.getId(), b.getId(), LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31),
                Separation.Type.VOLUNTARY, compensation.getId(), false, null, null);
        withdrawn.withdraw("Stayed on");
        separations.save(withdrawn);

        mvc.perform(get("/api/analytics/attrition?from=2026-05-01&to=2026-05-31").header("Authorization", bearer(hr)))
                .andExpect(jsonPath("$.kpis.separations").value(1));
    }

    @Test
    void onlyNoticePeriodResignationsCanBeWithdrawn() throws Exception {
        Employee gone = employees.save(new Employee(org.getId(), "E2", "Karan Rao", sales.getId(), LocalDate.of(2022, 1, 1)));
        long id = recordExit(gone, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
        mvc.perform(post("/api/separations/" + id + "/withdraw").header("Authorization", bearer(hr)))
                .andExpect(status().isConflict());

        String ceo = fx.token(mvc, fx.newUser(org, Role.LEADERSHIP));
        Employee nina = employees.save(new Employee(org.getId(), "E3", "Nina", sales.getId(), LocalDate.of(2022, 1, 1)));
        long open = recordExit(nina, LocalDate.now().minusDays(1), LocalDate.now().plusDays(30));
        mvc.perform(post("/api/separations/" + open + "/withdraw").header("Authorization", bearer(ceo)))
                .andExpect(status().isForbidden());
    }
}
