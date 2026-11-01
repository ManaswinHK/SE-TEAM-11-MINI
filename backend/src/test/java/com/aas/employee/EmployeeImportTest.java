package com.aas.employee;

import static com.aas.TestFixtures.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aas.TestFixtures;
import com.aas.auth.Role;
import com.aas.org.Department;
import com.aas.org.DepartmentRepository;
import com.aas.org.Organization;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmployeeImportTest {

    static final String HEADER = "employeeCode,fullName,email,department,designation,joinDate,performanceRating\n";

    @Autowired MockMvc mvc;
    @Autowired TestFixtures fx;
    @Autowired DepartmentRepository departments;
    @Autowired EmployeeRepository employees;

    Organization org;
    String hr;

    @BeforeEach
    void setUp() throws Exception {
        org = fx.newOrg();
        departments.save(new Department(org.getId(), "Sales", "Mumbai"));
        departments.save(new Department(org.getId(), "Finance", "Pune"));
        hr = fx.token(mvc, fx.newUser(org, Role.HR_MANAGER));
    }

    private ResultActions upload(String token, String csv) throws Exception {
        return mvc.perform(multipart("/api/employees/import")
                .file(new MockMultipartFile("file", "employees.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", bearer(token)));
    }

    /** TC-EMP-02: 1,000 rows of which 3 have invalid dates -> 997 imported and the 3 rows reported. */
    @Test
    void importsValidRowsAndReportsInvalidOnes() throws Exception {
        StringBuilder csv = new StringBuilder(HEADER);
        for (int i = 1; i <= 1000; i++) {
            String date = (i == 10 || i == 500 || i == 999) ? "31-02-2024" : "2023-05-01";
            csv.append("E").append(i).append(",Person ").append(i).append(",p").append(i)
                    .append("@x.test,").append(i % 2 == 0 ? "Sales" : "finance").append(",Executive,").append(date).append(",3\n");
        }
        upload(hr, csv.toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(1000))
                .andExpect(jsonPath("$.imported").value(997))
                .andExpect(jsonPath("$.rejected").value(3))
                .andExpect(jsonPath("$.errors[0].row").value(11))
                .andExpect(jsonPath("$.errors[0].employeeCode").value("E10"))
                .andExpect(jsonPath("$.errors[0].message").value("joinDate must be a date in YYYY-MM-DD format"))
                .andExpect(jsonPath("$.errors[2].row").value(1000));
        assertThat(employees.findByOrgId(org.getId())).hasSize(997);
    }

    @Test
    void rejectsDuplicatesUnknownDepartmentsBadRatingsAndFormulas() throws Exception {
        String csv = HEADER
                + "A1,Ok Person,a1@x.test,Sales,Exec,2023-01-01,4\n"
                + "A1,Duplicate In File,,Sales,Exec,2023-01-01,\n"
                + "A2,No Dept,,Marketing,Exec,2023-01-01,\n"
                + "A3,Bad Rating,,Sales,Exec,2023-01-01,7\n"
                + "A4,=HYPERLINK(\"http://evil\"),,Sales,Exec,2023-01-01,\n"
                + "\"A5\",\"Quoted, Name\",,Sales,\"Exec, Senior\",2023-01-01,\n"
                + "A6,,,Sales,Exec,2023-01-01,\n";
        upload(hr, csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(2))
                .andExpect(jsonPath("$.rejected").value(5))
                .andExpect(jsonPath("$.errors[0].message").value("employeeCode A1 already exists"))
                .andExpect(jsonPath("$.errors[1].message").value("Unknown department 'Marketing'"))
                .andExpect(jsonPath("$.errors[2].message").value("performanceRating must be a whole number from 1 to 5"))
                .andExpect(jsonPath("$.errors[3].message").value("fullName starts with '='; formulas are not allowed"))
                .andExpect(jsonPath("$.errors[4].message").value("fullName is required"));

        // Re-importing the same file adds nothing: every code already exists.
        upload(hr, HEADER + "A1,Ok Person,,Sales,Exec,2023-01-01,\n")
                .andExpect(jsonPath("$.imported").value(0))
                .andExpect(jsonPath("$.rejected").value(1));
    }

    @Test
    void rejectsFilesWithoutRequiredColumns() throws Exception {
        String body = upload(hr, "name,dept\nX,Sales\n")
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("Missing required column(s): department, employeeCode, fullName, joinDate");
    }

    @Test
    void templateDownloadAndRoleCheck() throws Exception {
        String template = mvc.perform(get("/api/employees/import/template").header("Authorization", bearer(hr)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(template).startsWith("employeeCode,fullName,email,department");

        String ceo = fx.token(mvc, fx.newUser(org, Role.LEADERSHIP));
        upload(ceo, HEADER).andExpect(status().isForbidden());
    }
}
