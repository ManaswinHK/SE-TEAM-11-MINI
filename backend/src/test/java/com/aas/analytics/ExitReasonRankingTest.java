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
import com.aas.org.Department;
import com.aas.org.DepartmentRepository;
import com.aas.org.ExitReason;
import com.aas.org.ExitReasonRepository;
import com.aas.org.Organization;
import com.aas.separation.Separation;
import com.aas.separation.SeparationRepository;
import com.aas.survey.SurveyResponse;
import com.aas.survey.SurveyResponseRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
class ExitReasonRankingTest {

    static final String PERIOD = "from=2026-01-01&to=2026-06-30";

    @Autowired MockMvc mvc;
    @Autowired TestFixtures fx;
    @Autowired DepartmentRepository departments;
    @Autowired EmployeeRepository employees;
    @Autowired ExitReasonRepository reasons;
    @Autowired SeparationRepository separations;
    @Autowired SurveyResponseRepository responses;

    Organization org;
    Department sales;
    Department finance;
    ExitReason pay;
    ExitReason growth;
    ExitReason manager;
    String hr;
    int nextCode;

    @BeforeEach
    void setUp() throws Exception {
        org = fx.newOrg();
        sales = departments.save(new Department(org.getId(), "Sales", "Mumbai"));
        finance = departments.save(new Department(org.getId(), "Finance", "Pune"));
        pay = reasons.save(new ExitReason(org.getId(), "Compensation", ExitReason.Category.VOLUNTARY));
        growth = reasons.save(new ExitReason(org.getId(), "Career growth", ExitReason.Category.VOLUNTARY));
        manager = reasons.save(new ExitReason(org.getId(), "Manager / leadership", ExitReason.Category.VOLUNTARY));
        hr = fx.token(mvc, fx.newUser(org, Role.HR_MANAGER));
    }

    private Separation exit(Department dept, ExitReason reason) {
        Employee e = employees.save(new Employee(org.getId(), "E" + nextCode++, "Person", dept.getId(), LocalDate.of(2022, 1, 1)));
        return separations.save(new Separation(org.getId(), e.getId(), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                Separation.Type.VOLUNTARY, reason.getId(), false, null, null));
    }

    private void answer(Separation s, int score) {
        responses.save(new SurveyResponse(org.getId(), s.getId(), score, score, score, score, 3, "Private comment", LocalDateTime.now()));
    }

    private String managerToken() throws Exception {
        AppUser user = fx.newUser(org, Role.DEPT_MANAGER);
        user.setDepartmentId(sales.getId());
        fx.save(user);
        return fx.token(mvc, user);
    }

    /** TC-ANA-04: pay 6, growth 4, manager 2 ranks in that order with 50 / 33 / 17 % shares. */
    @Test
    void ranksReasonsByFrequencyWithShare() throws Exception {
        for (int i = 0; i < 2; i++) exit(sales, manager);
        for (int i = 0; i < 6; i++) exit(sales, pay);
        for (int i = 0; i < 4; i++) exit(finance, growth);

        mvc.perform(get("/api/analytics/exit-reasons?" + PERIOD).header("Authorization", bearer(hr)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasons.length()").value(3))
                .andExpect(jsonPath("$.reasons[0].reason").value("Compensation"))
                .andExpect(jsonPath("$.reasons[0].separations").value(6))
                .andExpect(jsonPath("$.reasons[0].share").value(50.0))
                .andExpect(jsonPath("$.reasons[1].reason").value("Career growth"))
                .andExpect(jsonPath("$.reasons[1].share").value(33.33))
                .andExpect(jsonPath("$.reasons[2].reason").value("Manager / leadership"))
                .andExpect(jsonPath("$.reasons[2].share").value(16.67));
    }

    @Test
    void averagesSurveyScoresPerCategoryAndHidesFreeText() throws Exception {
        answer(exit(sales, pay), 2);
        answer(exit(sales, pay), 5);
        exit(sales, pay);  // never answered

        String body = mvc.perform(get("/api/analytics/exit-reasons?" + PERIOD).header("Authorization", bearer(hr)))
                .andExpect(jsonPath("$.survey.responses").value(2))
                .andExpect(jsonPath("$.survey.insufficientData").value(false))
                .andExpect(jsonPath("$.survey.pay").value(3.5))
                .andExpect(jsonPath("$.survey.culture").value(3.0))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("Private comment");
    }

    /** TC-SEC-07: a manager with 3 responses sees insufficient data; with 5 the averages appear. */
    @Test
    void departmentManagersNeedAtLeastFiveResponses() throws Exception {
        for (int i = 0; i < 3; i++) answer(exit(sales, pay), 4);
        for (int i = 0; i < 6; i++) answer(exit(finance, pay), 1);  // another department's answers do not count
        String token = managerToken();

        mvc.perform(get("/api/analytics/exit-reasons?" + PERIOD).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.reasons[0].separations").value(3))
                .andExpect(jsonPath("$.survey.responses").value(3))
                .andExpect(jsonPath("$.survey.insufficientData").value(true))
                .andExpect(jsonPath("$.survey.pay").doesNotExist());

        for (int i = 0; i < 2; i++) answer(exit(sales, pay), 4);
        mvc.perform(get("/api/analytics/exit-reasons?" + PERIOD).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.survey.insufficientData").value(false))
                .andExpect(jsonPath("$.survey.pay").value(4.0));
    }

    @Test
    void withdrawnResignationsAreLeftOut() throws Exception {
        Separation s = exit(sales, pay);
        s.withdraw(null);
        separations.save(s);
        exit(sales, growth);

        mvc.perform(get("/api/analytics/exit-reasons?" + PERIOD).header("Authorization", bearer(hr)))
                .andExpect(jsonPath("$.reasons.length()").value(1))
                .andExpect(jsonPath("$.reasons[0].reason").value("Career growth"))
                .andExpect(jsonPath("$.reasons[0].share").value(100.0));
    }
}
