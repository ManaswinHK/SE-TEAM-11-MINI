package com.aas.employee;

import static com.aas.TestFixtures.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aas.TestFixtures;
import com.aas.auth.Role;
import com.aas.org.Department;
import com.aas.org.DepartmentRepository;
import com.aas.org.Organization;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** TC-EMP-04: monthly snapshot per department with correct counts. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HeadcountSnapshotTest {

    @Autowired MockMvc mvc;
    @Autowired TestFixtures fx;
    @Autowired DepartmentRepository departments;
    @Autowired EmployeeRepository employees;
    @Autowired HeadcountService headcount;
    @Autowired HeadcountSnapshotRepository snapshots;

    Organization org;
    Department sales;
    Department finance;

    @BeforeEach
    void setUp() {
        org = fx.newOrg();
        sales = departments.save(new Department(org.getId(), "Sales", "Mumbai"));
        finance = departments.save(new Department(org.getId(), "Finance", "Pune"));
        add("S1", sales, "2023-01-10", null, EmployeeStatus.ACTIVE);
        add("S2", sales, "2024-06-01", "2026-03-31", EmployeeStatus.SEPARATED);  // left before 1 Jun 2026
        add("S3", sales, "2026-07-01", null, EmployeeStatus.ACTIVE);            // joins after 1 Jun 2026
        add("S4", sales, "2022-02-01", null, EmployeeStatus.INACTIVE);          // record removed by HR
        add("F1", finance, "2021-04-01", "2026-06-01", EmployeeStatus.SEPARATED); // last day is 1 Jun: still counted
        add("F2", finance, "2025-01-01", null, EmployeeStatus.ACTIVE);
    }

    private void add(String code, Department d, String joined, String lastDay, EmployeeStatus status) {
        Employee e = new Employee(org.getId(), code, code, d.getId(), LocalDate.parse(joined));
        e.setStatus(status);
        e.setLastWorkingDay(lastDay == null ? null : LocalDate.parse(lastDay));
        employees.save(e);
    }

    @Test
    void countsPeopleEmployedOnTheSnapshotDate() {
        LocalDate june1 = LocalDate.of(2026, 6, 1);
        Map<Long, Integer> counts = headcount.takeSnapshot(org.getId(), june1);

        assertThat(counts).containsEntry(sales.getId(), 1).containsEntry(finance.getId(), 2);
        assertThat(headcount.headcountOn(org.getId(), null, june1)).isEqualTo(3);
        assertThat(headcount.headcountOn(org.getId(), null, LocalDate.of(2026, 7, 1))).isEqualTo(3); // S3 in, F1 out
    }

    @Test
    void retakingASnapshotUpdatesItInsteadOfDuplicating() {
        LocalDate june1 = LocalDate.of(2026, 6, 1);
        headcount.takeSnapshot(org.getId(), june1);
        add("S5", sales, "2026-05-01", null, EmployeeStatus.ACTIVE);
        headcount.takeSnapshot(org.getId(), june1);

        var rows = snapshots.findByOrgIdAndSnapshotDateBetweenOrderBySnapshotDate(org.getId(), june1, june1);
        assertThat(rows).hasSize(2);
        assertThat(rows.stream().filter(r -> r.getDepartmentId().equals(sales.getId())).findFirst().orElseThrow().getHeadcount())
                .isEqualTo(2);
    }

    @Test
    void averageHeadcountIsMeanOfOpeningAndClosing() {
        assertThat(HeadcountService.average(236, 244)).isEqualByComparingTo(new BigDecimal("240.00"));
        assertThat(HeadcountService.average(3, 4)).isEqualByComparingTo(new BigDecimal("3.50"));
    }

    @Test
    void adminTakesSnapshotThroughApiAndHrReadsTotals() throws Exception {
        String admin = fx.token(mvc, fx.newUser(org, Role.HR_ADMIN));
        mvc.perform(post("/api/headcount/snapshots?date=2026-06-01").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        String hr = fx.token(mvc, fx.newUser(org, Role.HR_MANAGER));
        mvc.perform(get("/api/headcount/snapshots/totals?from=2026-01-01&to=2026-12-31").header("Authorization", bearer(hr)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value("2026-06-01"))
                .andExpect(jsonPath("$[0].headcount").value(3));
        mvc.perform(post("/api/headcount/snapshots").header("Authorization", bearer(hr))).andExpect(status().isForbidden());
    }
}
