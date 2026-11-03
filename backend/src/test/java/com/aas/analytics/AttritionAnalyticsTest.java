package com.aas.analytics;

import static com.aas.TestFixtures.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aas.TestFixtures;
import com.aas.auth.AppUser;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
class AttritionAnalyticsTest {

    static final String Q3 = "from=2026-07-01&to=2026-09-30";

    @Autowired MockMvc mvc;
    @Autowired TestFixtures fx;
    @Autowired DepartmentRepository departments;
    @Autowired EmployeeRepository employees;
    @Autowired ExitReasonRepository reasons;
    @Autowired SeparationRepository separations;

    Organization org;
    Department sales;
    Department finance;
    String hr;

    /**
     * Q3 2026: 236 people at the start (140 Sales in Mumbai, 96 Finance in Pune), 12 leave during the quarter
     * (8 Sales, 4 Finance; 9 voluntary, 6 regretted) and 20 join Sales, so 244 at the end.
     */
    @BeforeEach
    void setUp() throws Exception {
        org = fx.newOrg();
        sales = departments.save(new Department(org.getId(), "Sales", "Mumbai"));
        finance = departments.save(new Department(org.getId(), "Finance", "Pune"));
        ExitReason pay = reasons.save(new ExitReason(org.getId(), "Compensation", ExitReason.Category.VOLUNTARY));
        ExitReason perf = reasons.save(new ExitReason(org.getId(), "Performance", ExitReason.Category.INVOLUNTARY));

        List<Employee> staff = new ArrayList<>();
        for (int i = 0; i < 236; i++) {
            boolean isSales = i < 140;
            // join dates spread so tenure bands differ: 0.5, 2, 4 and 6 years before the quarter
            LocalDate joined = LocalDate.of(2026, 1, 1).minusMonths(new int[] {0, 18, 42, 66}[i % 4]);
            Employee e = new Employee(org.getId(), "E" + i, "Person " + i, isSales ? sales.getId() : finance.getId(), joined);
            e.setLocation(isSales ? "Mumbai" : "Pune");
            staff.add(employees.save(e));
        }
        for (int i = 0; i < 20; i++) {
            Employee e = new Employee(org.getId(), "N" + i, "New " + i, sales.getId(), LocalDate.of(2026, 8, 3));
            e.setLocation("Mumbai");
            employees.save(e);
        }
        int[] leavers = {0, 1, 2, 3, 4, 5, 6, 7, 140, 141, 142, 143};  // 8 Sales + 4 Finance
        for (int n = 0; n < leavers.length; n++) {
            Employee e = staff.get(leavers[n]);
            boolean voluntary = n < 9;
            LocalDate lastDay = LocalDate.of(2026, 8, 1).plusDays(n);
            separations.save(new Separation(org.getId(), e.getId(), lastDay.minusDays(30), lastDay,
                    voluntary ? Separation.Type.VOLUNTARY : Separation.Type.INVOLUNTARY,
                    voluntary ? pay.getId() : perf.getId(), n < 6, null, null));
            e.setLastWorkingDay(lastDay);
            e.setStatus(EmployeeStatus.SEPARATED);
            employees.save(e);
        }
        hr = fx.token(mvc, fx.newUser(org, Role.HR_MANAGER));
    }

    @Test
    void formulaMatchesWorkedExample() {
        assertThat(AttritionCalculator.averageHeadcount(236, 244)).isEqualByComparingTo("240.00");
        assertThat(AttritionCalculator.rate(12, new BigDecimal("240"))).isEqualByComparingTo("5.00");
        assertThat(AttritionCalculator.rate(3, BigDecimal.ZERO)).isEqualByComparingTo("0.00");
        assertThat(AttritionCalculator.tenureBand(11)).isEqualTo("< 1 year");
        assertThat(AttritionCalculator.tenureBand(12)).isEqualTo("1-3 years");
        assertThat(AttritionCalculator.tenureBand(60)).isEqualTo("> 5 years");
    }

    /** TC-ANA-01: 12 separations, opening 236, closing 244 -> 5.00 %. */
    @Test
    void dashboardShowsAttritionVoluntaryAndRegrettedRates() throws Exception {
        mvc.perform(get("/api/analytics/attrition?" + Q3).header("Authorization", bearer(hr)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kpis.openingHeadcount").value(236))
                .andExpect(jsonPath("$.kpis.closingHeadcount").value(244))
                .andExpect(jsonPath("$.kpis.averageHeadcount").value(240.0))
                .andExpect(jsonPath("$.kpis.separations").value(12))
                .andExpect(jsonPath("$.kpis.attritionRate").value(5.0))
                .andExpect(jsonPath("$.kpis.voluntaryRate").value(3.75))
                .andExpect(jsonPath("$.kpis.regrettedRate").value(2.5));
    }

    /** TC-ANA-02: each breakdown's separations add up to the overall total. */
    @Test
    void breakdownsAddUpToTheTotal() throws Exception {
        mvc.perform(get("/api/analytics/attrition?" + Q3).header("Authorization", bearer(hr)))
                .andExpect(jsonPath("$.byDepartment.length()").value(2))
                .andExpect(jsonPath("$.byDepartment[?(@.key=='Sales')].separations").value(8))
                .andExpect(jsonPath("$.byDepartment[?(@.key=='Finance')].separations").value(4))
                // Sales: opening 140, closing 140 - 8 + 20 = 152 -> avg 146 -> 5.48 %
                .andExpect(jsonPath("$.byDepartment[?(@.key=='Sales')].attritionRate").value(5.48))
                .andExpect(jsonPath("$.byLocation[?(@.key=='Pune')].separations").value(4))
                .andExpect(jsonPath("$.byTenure.length()").value(4))
                .andExpect(jsonPath("$.byTenure[0].band").value("< 1 year"))
                .andExpect(jsonPath("$.byTenure[0].separations").value(3))
                .andExpect(jsonPath("$.byTenure[0].share").value(25.0));
    }

    @Test
    void filterByDepartmentAndScopeForManagers() throws Exception {
        mvc.perform(get("/api/analytics/attrition?" + Q3 + "&departmentId=" + finance.getId()).header("Authorization", bearer(hr)))
                .andExpect(jsonPath("$.kpis.separations").value(4))
                .andExpect(jsonPath("$.kpis.openingHeadcount").value(96))
                .andExpect(jsonPath("$.departmentId").value(finance.getId()));

        AppUser manager = fx.newUser(org, Role.DEPT_MANAGER);
        manager.setDepartmentId(sales.getId());
        fx.save(manager);
        String token = fx.token(mvc, manager);
        mvc.perform(get("/api/analytics/attrition?" + Q3).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.kpis.separations").value(8))
                .andExpect(jsonPath("$.departmentId").value(sales.getId()));
        mvc.perform(get("/api/analytics/attrition?" + Q3 + "&departmentId=" + finance.getId()).header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());

        String ceo = fx.token(mvc, fx.newUser(org, Role.LEADERSHIP));
        mvc.perform(get("/api/analytics/attrition?" + Q3).header("Authorization", bearer(ceo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kpis.attritionRate").value(5.0));
    }

    @Test
    void invalidRangeIsRejected() throws Exception {
        mvc.perform(get("/api/analytics/attrition?from=2026-09-30&to=2026-07-01").header("Authorization", bearer(hr)))
                .andExpect(status().isBadRequest());
    }
}
