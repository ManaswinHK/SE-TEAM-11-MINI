package com.aas.analytics;

import static com.aas.TestFixtures.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.aas.separation.Separation;
import com.aas.separation.SeparationRepository;
import java.time.LocalDate;
import java.time.YearMonth;
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
class AttritionTrendTest {

    @Autowired MockMvc mvc;
    @Autowired TestFixtures fx;
    @Autowired DepartmentRepository departments;
    @Autowired EmployeeRepository employees;
    @Autowired ExitReasonRepository reasons;
    @Autowired SeparationRepository separations;

    Organization org;
    ExitReason pay;
    List<Employee> staff = new ArrayList<>();
    String hr;

    /** 100 people who joined in 2020; exits are added per test. */
    @BeforeEach
    void setUp() throws Exception {
        org = fx.newOrg();
        Department sales = departments.save(new Department(org.getId(), "Sales", "Mumbai"));
        pay = reasons.save(new ExitReason(org.getId(), "Compensation", ExitReason.Category.VOLUNTARY));
        for (int i = 0; i < 100; i++) {
            staff.add(employees.save(new Employee(org.getId(), "E" + i, "Person " + i, sales.getId(), LocalDate.of(2020, 1, 1))));
        }
        hr = fx.token(mvc, fx.newUser(org, Role.HR_MANAGER));
    }

    private void leave(Employee e, LocalDate lastDay) {
        separations.save(new Separation(org.getId(), e.getId(), lastDay.minusDays(30), lastDay, Separation.Type.VOLUNTARY,
                pay.getId(), false, null, null));
        e.setLastWorkingDay(lastDay);
        e.setStatus(EmployeeStatus.SEPARATED);
        employees.save(e);
    }

    /**
     * TC-ANA-03: 12 monthly points ending this month. Two months ago 2 people left (99 at the start, 97 at the end,
     * so 2 / 98 = 2.04%); in the same month last year 1 person left (100 -> 99, 1 / 99.5 = 1.01%).
     */
    @Test
    void monthlyRatesComparedWithPreviousYear() throws Exception {
        YearMonth target = YearMonth.now().minusMonths(2);
        leave(staff.get(0), target.minusYears(1).atDay(15));
        leave(staff.get(1), target.atDay(10));
        leave(staff.get(2), target.atDay(20));

        mvc.perform(get("/api/analytics/trend").header("Authorization", bearer(hr)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(12))
                .andExpect(jsonPath("$[11].month").value(YearMonth.now().toString()))
                .andExpect(jsonPath("$[9].month").value(target.toString()))
                .andExpect(jsonPath("$[9].separations").value(2))
                .andExpect(jsonPath("$[9].averageHeadcount").value(98.0))
                .andExpect(jsonPath("$[9].attritionRate").value(2.04))
                .andExpect(jsonPath("$[9].previousYearSeparations").value(1))
                .andExpect(jsonPath("$[9].previousYearRate").value(1.01))
                .andExpect(jsonPath("$[10].separations").value(0));
    }

    @Test
    void rejectsOutOfRangeMonths() throws Exception {
        mvc.perform(get("/api/analytics/trend?months=0").header("Authorization", bearer(hr)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/analytics/trend?months=6").header("Authorization", bearer(hr)))
                .andExpect(jsonPath("$.length()").value(6));
    }
}
