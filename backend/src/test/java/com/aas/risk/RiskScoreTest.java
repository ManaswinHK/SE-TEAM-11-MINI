package com.aas.risk;

import static com.aas.TestFixtures.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class RiskScoreTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 2);

    @Autowired MockMvc mvc;
    @Autowired TestFixtures fx;
    @Autowired DepartmentRepository departments;
    @Autowired EmployeeRepository employees;
    @Autowired ExitReasonRepository reasons;
    @Autowired SeparationRepository separations;
    @Autowired RiskScoreRepository scores;
    @Autowired RiskService service;

    Organization org;
    Department sales;
    Department finance;
    String hr;
    int code;

    @BeforeEach
    void setUp() throws Exception {
        org = fx.newOrg();
        sales = departments.save(new Department(org.getId(), "Sales", "Mumbai"));
        finance = departments.save(new Department(org.getId(), "Finance", "Pune"));
        hr = fx.token(mvc, fx.newUser(org, Role.HR_MANAGER));
    }

    private Employee person(Department d, LocalDate joined, LocalDate promoted, Integer rating) {
        Employee e = new Employee(org.getId(), "E" + code++, "Person " + code, d.getId(), joined);
        e.setLastPromotionDate(promoted);
        e.setPerformanceRating(rating);
        return employees.save(e);
    }

    @Test
    void modelBandsMatchTheSrsThresholds() {
        assertThat(RiskModel.band(39)).isEqualTo(RiskModel.Band.LOW);
        assertThat(RiskModel.band(40)).isEqualTo(RiskModel.Band.MEDIUM);
        assertThat(RiskModel.band(69)).isEqualTo(RiskModel.Band.MEDIUM);
        assertThat(RiskModel.band(70)).isEqualTo(RiskModel.Band.HIGH);

        // Joined 2 years ago, never promoted, top rating, team losing 25% a year: 10 + 18 + 15 + 20 + 25 = 88
        RiskModel.Result high = RiskModel.score(TODAY.minusYears(2), null, 5, new BigDecimal("25.00"), TODAY);
        assertThat(high.score()).isEqualTo(88);
        assertThat(high.band()).isEqualTo(RiskModel.Band.HIGH);
        // 8 years in, promoted 6 months ago, average rating, stable team: base only
        RiskModel.Result low = RiskModel.score(TODAY.minusYears(8), TODAY.minusMonths(6), 3, BigDecimal.ZERO, TODAY);
        assertThat(low.score()).isEqualTo(10);
        assertThat(low.band()).isEqualTo(RiskModel.Band.LOW);
    }

    /** TC-RSK-01: every active employee gets a 0-100 score dated today with the matching band. */
    @Test
    void weeklyJobScoresEveryActiveEmployee() throws Exception {
        for (int i = 0; i < 6; i++) person(sales, LocalDate.of(2020 + i % 5, 3, 1), null, 1 + i % 5);
        Employee gone = person(sales, LocalDate.of(2021, 1, 1), null, 3);
        gone.setStatus(EmployeeStatus.SEPARATED);
        employees.save(gone);

        assertThat(service.scoreOrganization(org.getId(), TODAY)).isEqualTo(6);
        assertThat(scores.findByOrgIdAndScoredOn(org.getId(), TODAY)).hasSize(6).allSatisfy(r -> {
            assertThat(r.getScore()).isBetween(0, 100);
            assertThat(r.getBand()).isEqualTo(RiskModel.band(r.getScore()));
            assertThat(r.getEmployeeId()).isNotEqualTo(gone.getId());
        });

        service.scoreOrganization(org.getId(), TODAY);  // a second run the same day replaces the first
        assertThat(scores.findByOrgIdAndScoredOn(org.getId(), TODAY)).hasSize(6);
    }

    @Test
    void teamAttritionRaisesTheScore() {
        ExitReason pay = reasons.save(new ExitReason(org.getId(), "Compensation", ExitReason.Category.VOLUNTARY));
        Employee stayer = person(sales, TODAY.minusYears(2), null, 5);
        Employee twin = person(finance, TODAY.minusYears(2), null, 5);
        for (int i = 0; i < 3; i++) {  // 3 of 5 Sales people left this year
            Employee left = person(sales, LocalDate.of(2022, 1, 1), null, 3);
            separations.save(new Separation(org.getId(), left.getId(), TODAY.minusMonths(3), TODAY.minusMonths(2),
                    Separation.Type.VOLUNTARY, pay.getId(), false, null, null));
            left.setLastWorkingDay(TODAY.minusMonths(2));
            left.setStatus(EmployeeStatus.SEPARATED);
            employees.save(left);
        }
        person(finance, LocalDate.of(2015, 1, 1), TODAY.minusMonths(2), 3);

        service.scoreOrganization(org.getId(), TODAY);
        int salesScore = scores.findByOrgIdAndScoredOn(org.getId(), TODAY).stream()
                .filter(r -> r.getEmployeeId().equals(stayer.getId())).findFirst().orElseThrow().getScore();
        int financeScore = scores.findByOrgIdAndScoredOn(org.getId(), TODAY).stream()
                .filter(r -> r.getEmployeeId().equals(twin.getId())).findFirst().orElseThrow().getScore();
        assertThat(salesScore).isEqualTo(financeScore + 25);
    }

    /** TC-RSK-02: a High-risk employee's card shows the top 3 factors with their contribution. */
    @Test
    void highRiskEmployeeShowsTopThreeFactors() throws Exception {
        // Joined 2 years ago, never promoted, top rating: tenure 15, no promotion 18, high performer 20 (+ base 10)
        person(sales, LocalDate.now().minusYears(2).minusDays(10), null, 5);
        mvc.perform(post("/api/risk/run").header("Authorization", bearer(hr))).andExpect(status().isOk());

        mvc.perform(get("/api/risk?band=MEDIUM").header("Authorization", bearer(hr)))
                .andExpect(jsonPath("$[0].score").value(63))
                .andExpect(jsonPath("$[0].factors.length()").value(3))
                .andExpect(jsonPath("$[0].factors[0].label").value("High performer without a recent promotion"))
                .andExpect(jsonPath("$[0].factors[0].points").value(20))
                .andExpect(jsonPath("$[0].factors[0].share").value(32))
                .andExpect(jsonPath("$[0].factors[1].points").value(18))
                .andExpect(jsonPath("$[0].factors[2].points").value(15));
    }

    @Test
    void listIsScopedAndRestricted() throws Exception {
        person(sales, TODAY.minusYears(2), null, 5);
        person(finance, TODAY.minusYears(2), null, 5);
        mvc.perform(post("/api/risk/run").header("Authorization", bearer(hr)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scored").value(2));
        mvc.perform(get("/api/risk").header("Authorization", bearer(hr)))
                .andExpect(jsonPath("$.length()").value(2));

        AppUser manager = fx.newUser(org, Role.DEPT_MANAGER);
        manager.setDepartmentId(sales.getId());
        fx.save(manager);
        mvc.perform(get("/api/risk").header("Authorization", bearer(fx.token(mvc, manager))))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].departmentName").value("Sales"));

        String ceo = fx.token(mvc, fx.newUser(org, Role.LEADERSHIP));
        mvc.perform(get("/api/risk").header("Authorization", bearer(ceo))).andExpect(status().isForbidden());
        String outsider = fx.token(mvc, fx.newUser(fx.newOrg(), Role.HR_MANAGER));
        mvc.perform(get("/api/risk").header("Authorization", bearer(outsider)))
                .andExpect(jsonPath("$.length()").value(0));
    }
}
