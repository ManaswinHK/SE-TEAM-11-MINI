package com.aas.risk;

import static com.aas.TestFixtures.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aas.TestFixtures;
import com.aas.auth.AppUser;
import com.aas.auth.CurrentUser;
import com.aas.auth.Role;
import com.aas.employee.Employee;
import com.aas.employee.EmployeeRepository;
import com.aas.org.Department;
import com.aas.org.DepartmentRepository;
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
class RetentionActionTest {

    @Autowired MockMvc mvc;
    @Autowired TestFixtures fx;
    @Autowired DepartmentRepository departments;
    @Autowired EmployeeRepository employees;
    @Autowired RetentionService service;

    Organization org;
    Department sales;
    Department finance;
    Employee riya;
    Employee arjun;
    AppUser managerUser;
    String manager;

    @BeforeEach
    void setUp() throws Exception {
        org = fx.newOrg();
        sales = departments.save(new Department(org.getId(), "Sales", "Mumbai"));
        finance = departments.save(new Department(org.getId(), "Finance", "Pune"));
        riya = employees.save(new Employee(org.getId(), "S1", "Riya Sharma", sales.getId(), LocalDate.of(2023, 1, 9)));
        arjun = employees.save(new Employee(org.getId(), "F1", "Arjun Nair", finance.getId(), LocalDate.of(2022, 1, 1)));
        managerUser = fx.newUser(org, Role.DEPT_MANAGER);
        managerUser.setDepartmentId(sales.getId());
        fx.save(managerUser);
        manager = fx.token(mvc, managerUser);
    }

    private ResultActions log(String token, Employee e, LocalDate due) throws Exception {
        return mvc.perform(post("/api/retention-actions").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"employeeId":%d,"action":"Career discussion","ownerName":"Meera Manager","dueDate":"%s"}"""
                        .formatted(e.getId(), due)));
    }

    /** TC-RSK-03: an action due tomorrow is saved, visible to HR, and overdue two days later. */
    @Test
    void actionBecomesOverdueAfterItsDueDate() throws Exception {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        log(manager, riya, tomorrow)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.overdue").value(false));

        String hr = fx.token(mvc, fx.newUser(org, Role.HR_MANAGER));
        mvc.perform(get("/api/retention-actions").header("Authorization", bearer(hr)))
                .andExpect(jsonPath("$[0].employeeName").value("Riya Sharma"))
                .andExpect(jsonPath("$[0].ownerName").value("Meera Manager"));

        CurrentUser hrUser = new CurrentUser(managerUser.getId(), org.getId(), "x", "x", Role.HR_MANAGER, null);
        assertThat(service.list(hrUser, null, LocalDate.now().plusDays(2)))
                .singleElement().satisfies(a -> assertThat(a.overdue()).isTrue());
    }

    @Test
    void completingRecordsTheOutcomeAndClearsOverdue() throws Exception {
        String body = log(manager, riya, LocalDate.now()).andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(body.replaceAll(".*?\"id\":(\\d+).*", "$1"));

        mvc.perform(post("/api/retention-actions/" + id + "/complete").header("Authorization", bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"Agreed a promotion plan\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.outcome").value("Agreed a promotion plan"));
        CurrentUser hrUser = new CurrentUser(managerUser.getId(), org.getId(), "x", "x", Role.HR_MANAGER, null);
        assertThat(service.list(hrUser, null, LocalDate.now().plusDays(5))).allSatisfy(a -> assertThat(a.overdue()).isFalse());

        mvc.perform(post("/api/retention-actions/" + id + "/complete").header("Authorization", bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void managersStayInTheirTeamAndDatesAreValidated() throws Exception {
        log(manager, arjun, LocalDate.now().plusDays(3)).andExpect(status().isForbidden());
        log(manager, riya, LocalDate.now().minusDays(1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("dueDate"));

        String ceo = fx.token(mvc, fx.newUser(org, Role.LEADERSHIP));
        mvc.perform(get("/api/retention-actions").header("Authorization", bearer(ceo))).andExpect(status().isForbidden());
        String outsider = fx.token(mvc, fx.newUser(fx.newOrg(), Role.HR_MANAGER));
        log(outsider, riya, LocalDate.now().plusDays(3)).andExpect(status().isNotFound());
    }
}
