package com.aas.separation;

import static com.aas.TestFixtures.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aas.TestFixtures;
import com.aas.auth.AppUser;
import com.aas.auth.Role;
import com.aas.common.AuditLogRepository;
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
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SeparationTest {

    @Autowired MockMvc mvc;
    @Autowired TestFixtures fx;
    @Autowired DepartmentRepository departments;
    @Autowired EmployeeRepository employees;
    @Autowired ExitReasonRepository reasons;
    @Autowired SeparationRepository separations;
    @Autowired SeparationService service;
    @Autowired AuditLogRepository audit;

    Organization org;
    Department sales;
    Department finance;
    ExitReason compensation;
    Employee riya;
    String hr;

    @BeforeEach
    void setUp() throws Exception {
        org = fx.newOrg();
        sales = departments.save(new Department(org.getId(), "Sales", "Mumbai"));
        finance = departments.save(new Department(org.getId(), "Finance", "Pune"));
        compensation = reasons.save(new ExitReason(org.getId(), "Compensation", ExitReason.Category.VOLUNTARY));
        riya = employees.save(new Employee(org.getId(), "E1001", "Riya Sharma", sales.getId(), LocalDate.of(2023, 1, 9)));
        hr = fx.token(mvc, fx.newUser(org, Role.HR_MANAGER));
    }

    private ResultActions record(String token, long employeeId, LocalDate resigned, LocalDate lastDay, long reasonId) throws Exception {
        return mvc.perform(post("/api/separations").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"employeeId":%d,"resignationDate":"%s","lastWorkingDay":"%s","type":"VOLUNTARY",
                         "reasonId":%d,"regretted":true,"remarks":"Better pay elsewhere"}"""
                        .formatted(employeeId, resigned, lastDay, reasonId)));
    }

    /** TC-SEP-01: voluntary resignation, reason, regretted -> 201, status Notice period, audit entry written. */
    @Test
    void recordsResignationAndPutsEmployeeOnNotice() throws Exception {
        LocalDate today = LocalDate.now();
        String id = record(hr, riya.getId(), today.minusDays(1), today.plusDays(30), compensation.getId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NOTICE_PERIOD"))
                .andExpect(jsonPath("$.employeeName").value("Riya Sharma"))
                .andExpect(jsonPath("$.departmentName").value("Sales"))
                .andExpect(jsonPath("$.reasonName").value("Compensation"))
                .andExpect(jsonPath("$.regretted").value(true))
                .andReturn().getResponse().getContentAsString().replaceAll(".*?\"id\":(\\d+).*", "$1");

        Employee after = employees.findById(riya.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(EmployeeStatus.NOTICE_PERIOD);
        assertThat(after.getLastWorkingDay()).isEqualTo(today.plusDays(30));
        assertThat(audit.findByOrgIdAndEntityAndEntityIdOrderByCreatedAt(org.getId(), "SEPARATION", Long.valueOf(id)))
                .singleElement().satisfies(a -> assertThat(a.getAction()).isEqualTo("CREATE"));
    }

    @Test
    void pastLastWorkingDayIsRecordedAsSeparated() throws Exception {
        record(hr, riya.getId(), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), compensation.getId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.tenureMonths").value(38));
        assertThat(employees.findById(riya.getId()).orElseThrow().getStatus()).isEqualTo(EmployeeStatus.SEPARATED);
    }

    /** TC-SEP-02: last working day before resignation date -> 400 with a field error, nothing saved. */
    @Test
    void rejectsLastWorkingDayBeforeResignation() throws Exception {
        record(hr, riya.getId(), LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 1), compensation.getId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("lastWorkingDay"));
        assertThat(separations.findAll().stream().filter(s -> s.getOrgId().equals(org.getId()))).isEmpty();
        assertThat(employees.findById(riya.getId()).orElseThrow().getStatus()).isEqualTo(EmployeeStatus.ACTIVE);
    }

    @Test
    void rejectsSecondSeparationAndOtherInvalidInput() throws Exception {
        record(hr, riya.getId(), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), compensation.getId())
                .andExpect(status().isCreated());
        record(hr, riya.getId(), LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), compensation.getId())
                .andExpect(status().isConflict());

        Employee karan = employees.save(new Employee(org.getId(), "E2", "Karan Rao", sales.getId(), LocalDate.of(2025, 6, 1)));
        record(hr, karan.getId(), LocalDate.of(2025, 1, 1), LocalDate.of(2025, 2, 1), compensation.getId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("resignationDate"));
        record(hr, karan.getId(), LocalDate.now().plusDays(3), LocalDate.now().plusDays(40), compensation.getId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("resignationDate"));
        record(hr, karan.getId(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), 999_999)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("reasonId"));
        record(hr, 999_999, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), compensation.getId())
                .andExpect(status().isNotFound());
    }

    @Test
    void departmentManagersCanViewTheirTeamButNotRecord() throws Exception {
        record(hr, riya.getId(), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), compensation.getId());
        Employee arjun = employees.save(new Employee(org.getId(), "F9", "Arjun Nair", finance.getId(), LocalDate.of(2022, 1, 1)));
        record(hr, arjun.getId(), LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), compensation.getId());

        AppUser manager = fx.newUser(org, Role.DEPT_MANAGER);
        manager.setDepartmentId(sales.getId());
        fx.save(manager);
        String token = fx.token(mvc, manager);

        mvc.perform(get("/api/separations?from=2026-01-01&to=2026-12-31").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].employeeCode").value("E1001"));
        mvc.perform(get("/api/separations?from=2026-01-01&to=2026-12-31").header("Authorization", bearer(hr)))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].employeeCode").value("F9"));
        Employee nina = employees.save(new Employee(org.getId(), "S7", "Nina", sales.getId(), LocalDate.of(2022, 1, 1)));
        record(token, nina.getId(), LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), compensation.getId())
                .andExpect(status().isForbidden());
    }

    @Test
    void dailyJobCompletesSeparationsWhoseLastDayHasPassed() throws Exception {
        LocalDate lastDay = LocalDate.now().plusDays(10);
        record(hr, riya.getId(), LocalDate.now().minusDays(5), lastDay, compensation.getId())
                .andExpect(jsonPath("$.status").value("NOTICE_PERIOD"));

        service.completeDueSeparations(lastDay);  // on the last working day the person is still employed
        assertThat(employees.findById(riya.getId()).orElseThrow().getStatus()).isEqualTo(EmployeeStatus.NOTICE_PERIOD);

        service.completeDueSeparations(lastDay.plusDays(1));
        assertThat(employees.findById(riya.getId()).orElseThrow().getStatus()).isEqualTo(EmployeeStatus.SEPARATED);
        assertThat(separations.findAll().stream().filter(x -> x.getEmployeeId().equals(riya.getId())))
                .singleElement().satisfies(x -> assertThat(x.getStatus()).isEqualTo(Separation.Status.COMPLETED));
    }
}
