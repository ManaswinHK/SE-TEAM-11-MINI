package com.aas.employee;

import static com.aas.TestFixtures.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aas.TestFixtures;
import com.aas.auth.AppUser;
import com.aas.auth.Role;
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
class EmployeeRecordsTest {

    @Autowired MockMvc mvc;
    @Autowired TestFixtures fx;
    @Autowired DepartmentRepository departments;
    @Autowired EmployeeRepository employees;

    Organization org;
    Department sales;
    Department finance;
    String hr;

    @BeforeEach
    void setUp() throws Exception {
        org = fx.newOrg();
        sales = departments.save(new Department(org.getId(), "Sales", "Mumbai"));
        finance = departments.save(new Department(org.getId(), "Finance", "Pune"));
        hr = fx.token(mvc, fx.newUser(org, Role.HR_MANAGER));
    }

    private String body(String code, long deptId, String joinDate, String extra) {
        return """
                {"employeeCode":"%s","fullName":"Test Person","email":"t@x.test","departmentId":%d,
                 "designation":"Executive","joinDate":"%s"%s}""".formatted(code, deptId, joinDate, extra);
    }

    private ResultActions create(String token, String json) throws Exception {
        return mvc.perform(post("/api/employees").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** TC-EMP-01: create, edit, duplicate ID rejected. */
    @Test
    void hrCreatesEditsAndDeactivatesEmployees() throws Exception {
        String id = create(hr, body("E1001", sales.getId(), "2024-01-15", ",\"performanceRating\":4"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.departmentName").value("Sales"))
                .andReturn().getResponse().getContentAsString().replaceAll(".*?\"id\":(\\d+).*", "$1");

        create(hr, body("e1001", finance.getId(), "2024-02-01", "")).andExpect(status().isConflict());

        mvc.perform(put("/api/employees/" + id).header("Authorization", bearer(hr)).contentType(MediaType.APPLICATION_JSON)
                        .content(body("E1001", sales.getId(), "2024-01-15", ",\"designation\":\"Senior Executive\"")
                                .replace("\"designation\":\"Executive\",", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designation").value("Senior Executive"));

        mvc.perform(post("/api/employees/" + id + "/deactivate").header("Authorization", bearer(hr)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void mandatoryFieldsAndDatesAreValidated() throws Exception {
        create(hr, "{\"fullName\":\"No Code\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        create(hr, body("E2", sales.getId(), LocalDate.now().plusDays(5).toString(), ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("joinDate"));
        create(hr, body("E3", sales.getId(), "2024-05-01", ",\"lastPromotionDate\":\"2023-01-01\""))
                .andExpect(status().isBadRequest());
        create(hr, body("E4", sales.getId(), "2024-05-01", ",\"performanceRating\":9"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchFiltersByNameDepartmentAndStatus() throws Exception {
        create(hr, body("S-1", sales.getId(), "2023-01-01", "").replace("Test Person", "Riya Sharma"));
        create(hr, body("F-1", finance.getId(), "2023-01-01", "").replace("Test Person", "Karan Rao"));

        mvc.perform(get("/api/employees?q=riya").header("Authorization", bearer(hr)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].employeeCode").value("S-1"));
        mvc.perform(get("/api/employees?departmentId=" + finance.getId()).header("Authorization", bearer(hr)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].fullName").value("Karan Rao"));
        mvc.perform(get("/api/employees?status=ACTIVE").header("Authorization", bearer(hr)))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    /** TC-AUTH-02 on real data: a Sales manager sees only Sales employees. */
    @Test
    void departmentManagerSeesOnlyOwnTeam() throws Exception {
        create(hr, body("S-1", sales.getId(), "2023-01-01", ""));
        String financeId = create(hr, body("F-1", finance.getId(), "2023-01-01", ""))
                .andReturn().getResponse().getContentAsString().replaceAll(".*?\"id\":(\\d+).*", "$1");

        AppUser manager = fx.newUser(org, Role.DEPT_MANAGER);
        manager.setDepartmentId(sales.getId());
        fx.save(manager);
        String token = fx.token(mvc, manager);

        mvc.perform(get("/api/employees").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].employeeCode").value("S-1"));
        mvc.perform(get("/api/employees?departmentId=" + finance.getId()).header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/employees/" + financeId).header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        create(token, body("S-2", sales.getId(), "2023-01-01", "")).andExpect(status().isForbidden());
    }

    @Test
    void leadershipAndOtherOrganizationsHaveNoAccess() throws Exception {
        String id = create(hr, body("E9", sales.getId(), "2023-01-01", ""))
                .andReturn().getResponse().getContentAsString().replaceAll(".*?\"id\":(\\d+).*", "$1");

        String ceo = fx.token(mvc, fx.newUser(org, Role.LEADERSHIP));
        mvc.perform(get("/api/employees").header("Authorization", bearer(ceo))).andExpect(status().isForbidden());

        Organization other = fx.newOrg();
        String otherHr = fx.token(mvc, fx.newUser(other, Role.HR_MANAGER));
        mvc.perform(get("/api/employees/" + id).header("Authorization", bearer(otherHr))).andExpect(status().isNotFound());
        mvc.perform(get("/api/employees").header("Authorization", bearer(otherHr)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
