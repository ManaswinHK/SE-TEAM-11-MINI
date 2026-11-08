package com.aas.alert;

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
import com.aas.org.OrganizationRepository;
import com.aas.separation.Separation;
import com.aas.separation.SeparationRepository;
import com.aas.survey.RecordingMailGateway;
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
class AttritionAlertTest {

    @Autowired MockMvc mvc;
    @Autowired TestFixtures fx;
    @Autowired OrganizationRepository orgs;
    @Autowired DepartmentRepository departments;
    @Autowired EmployeeRepository employees;
    @Autowired ExitReasonRepository reasons;
    @Autowired SeparationRepository separations;
    @Autowired AlertService service;
    @Autowired RecordingMailGateway mail;

    Organization org;
    Department sales;
    Department finance;
    ExitReason pay;
    List<Employee> salesStaff = new ArrayList<>();
    AppUser hr;
    AppUser salesManager;
    AppUser financeManager;

    /** Threshold 4%; Sales and Finance have 50 people each, joined in 2022. */
    @BeforeEach
    void setUp() {
        org = fx.newOrg();
        org.setRiskThreshold(new BigDecimal("4.00"));
        orgs.save(org);
        sales = departments.save(new Department(org.getId(), "Sales", "Mumbai"));
        finance = departments.save(new Department(org.getId(), "Finance", "Pune"));
        pay = reasons.save(new ExitReason(org.getId(), "Compensation", ExitReason.Category.VOLUNTARY));
        for (int i = 0; i < 50; i++) {
            salesStaff.add(employees.save(new Employee(org.getId(), "S" + i, "Sales " + i, sales.getId(), LocalDate.of(2022, 1, 1))));
            employees.save(new Employee(org.getId(), "F" + i, "Finance " + i, finance.getId(), LocalDate.of(2022, 1, 1)));
        }
        hr = fx.newUser(org, Role.HR_MANAGER);
        salesManager = fx.newUser(org, Role.DEPT_MANAGER);
        salesManager.setDepartmentId(sales.getId());
        fx.save(salesManager);
        financeManager = fx.newUser(org, Role.DEPT_MANAGER);
        financeManager.setDepartmentId(finance.getId());
        fx.save(financeManager);
    }

    private void salesLeave(int count, LocalDate lastDay) {
        for (int i = 0; i < count; i++) {
            Employee e = salesStaff.remove(0);
            separations.save(new Separation(org.getId(), e.getId(), lastDay.minusDays(30), lastDay, Separation.Type.VOLUNTARY,
                    pay.getId(), false, null, null));
            e.setLastWorkingDay(lastDay);
            e.setStatus(EmployeeStatus.SEPARATED);
            employees.save(e);
        }
    }

    /** TC-REP-01: Sales at 6% over 3 months against a 4% threshold raises one alert, in-app and by email. */
    @Test
    void breachRaisesOneAlertForHrAndTheDepartmentManager() throws Exception {
        LocalDate today = LocalDate.now();
        salesLeave(3, today.minusDays(20));  // 50 -> 47: 3 / 48.5 = 6.19%

        assertThat(service.check(org.getId(), today)).singleElement().satisfies(a -> {
            assertThat(a.getDepartmentId()).isEqualTo(sales.getId());
            assertThat(a.getRate()).isEqualByComparingTo("6.19");
        });
        assertThat(mail.to(hr.getEmail())).singleElement()
                .satisfies(m -> assertThat(m.subject()).isEqualTo("Attrition alert: Sales at 6.19%"));
        assertThat(mail.to(salesManager.getEmail())).hasSize(1);
        assertThat(mail.to(financeManager.getEmail())).isEmpty();

        // The same breach the next day does not alert again.
        assertThat(service.check(org.getId(), today)).isEmpty();
        assertThat(mail.to(hr.getEmail())).hasSize(1);

        mvc.perform(get("/api/alerts").header("Authorization", bearer(fx.token(mvc, salesManager))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].departmentName").value("Sales"))
                .andExpect(jsonPath("$[0].threshold").value(4.0))
                .andExpect(jsonPath("$[0].resolved").value(false));
        mvc.perform(get("/api/alerts").header("Authorization", bearer(fx.token(mvc, financeManager))))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void underThresholdRaisesNothingAndRecoveryResolvesTheAlert() {
        LocalDate today = LocalDate.now();
        salesLeave(1, today.minusDays(20));  // 1 / 49.5 = 2.02%
        assertThat(service.check(org.getId(), today)).isEmpty();

        salesLeave(2, today.minusDays(10));  // now 6.19%
        assertThat(service.check(org.getId(), today)).hasSize(1);

        // Four months on, nobody else has left: the rate is 0 and the alert is resolved.
        assertThat(service.check(org.getId(), today.plusMonths(4))).isEmpty();
        assertThat(service.recent(new com.aas.auth.CurrentUser(hr.getId(), org.getId(), "x", "x", Role.HR_MANAGER, null)))
                .singleElement().satisfies(a -> assertThat(a.resolved()).isTrue());
    }
}
