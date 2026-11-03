package com.aas.separation;

import static com.aas.TestFixtures.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aas.TestFixtures;
import com.aas.auth.Role;
import com.aas.employee.Employee;
import com.aas.employee.EmployeeRepository;
import com.aas.org.Department;
import com.aas.org.DepartmentRepository;
import com.aas.org.ExitReason;
import com.aas.org.ExitReasonRepository;
import com.aas.org.Organization;
import java.time.LocalDate;
import java.util.List;
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
class ClearanceTest {

    @Autowired MockMvc mvc;
    @Autowired TestFixtures fx;
    @Autowired DepartmentRepository departments;
    @Autowired EmployeeRepository employees;
    @Autowired ExitReasonRepository reasons;
    @Autowired ClearanceItemRepository items;
    @Autowired SeparationRepository separations;

    Organization org;
    Department sales;
    ExitReason compensation;
    String hr;

    @BeforeEach
    void setUp() throws Exception {
        org = fx.newOrg();
        sales = departments.save(new Department(org.getId(), "Sales", "Mumbai"));
        compensation = reasons.save(new ExitReason(org.getId(), "Compensation", ExitReason.Category.VOLUNTARY));
        hr = fx.token(mvc, fx.newUser(org, Role.HR_MANAGER));
    }

    private long recordExit(String code, LocalDate resigned, LocalDate lastDay) throws Exception {
        Employee e = employees.save(new Employee(org.getId(), code, "Person " + code, sales.getId(), LocalDate.of(2022, 1, 1)));
        String body = mvc.perform(post("/api/separations").header("Authorization", bearer(hr)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employeeId":%d,"resignationDate":"%s","lastWorkingDay":"%s","type":"VOLUNTARY","reasonId":%d,"regretted":false}"""
                                .formatted(e.getId(), resigned, lastDay, compensation.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*?\"id\":(\\d+).*", "$1"));
    }

    private ResultActions mark(long separationId, long itemId, boolean done) throws Exception {
        return mvc.perform(put("/api/separations/" + separationId + "/clearance/" + itemId)
                .header("Authorization", bearer(hr)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"done\":" + done + "}"));
    }

    private ResultActions close(long separationId) throws Exception {
        return mvc.perform(post("/api/separations/" + separationId + "/close").header("Authorization", bearer(hr)));
    }

    /** TC-SEP-03: the separation closes only when every clearance item is complete. */
    @Test
    void closesOnlyWhenEveryItemIsDone() throws Exception {
        long id = recordExit("E1", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
        mvc.perform(get("/api/separations/" + id + "/clearance").header("Authorization", bearer(hr)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.done").value(0))
                .andExpect(jsonPath("$.items[0].name").value("Laptop and ID card returned"));

        List<ClearanceItem> checklist = items.findBySeparationIdOrderBySortOrder(id);
        for (int i = 0; i < 4; i++) {
            mark(id, checklist.get(i).getId(), true).andExpect(status().isOk());
        }
        close(id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Clearance is not complete. Pending: Final settlement processed"));
        assertThat(separations.findById(id).orElseThrow().getStatus()).isEqualTo(Separation.Status.COMPLETED);

        mark(id, checklist.get(4).getId(), true).andExpect(jsonPath("$.done").value(5));
        close(id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
        assertThat(separations.findById(id).orElseThrow().getClosedAt()).isNotNull();

        // A closed checklist can no longer change.
        mark(id, checklist.get(0).getId(), false).andExpect(status().isConflict());
    }

    @Test
    void cannotCloseWhileServingNotice() throws Exception {
        long id = recordExit("E2", LocalDate.now().minusDays(2), LocalDate.now().plusDays(20));
        items.findBySeparationIdOrderBySortOrder(id).forEach(i -> {
            try {
                mark(id, i.getId(), true).andExpect(status().isOk());
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });
        close(id).andExpect(status().isConflict());
    }

    @Test
    void otherOrganizationsAndLeadershipCannotTouchTheChecklist() throws Exception {
        long id = recordExit("E3", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
        long itemId = items.findBySeparationIdOrderBySortOrder(id).get(0).getId();

        String outsider = fx.token(mvc, fx.newUser(fx.newOrg(), Role.HR_MANAGER));
        mvc.perform(get("/api/separations/" + id + "/clearance").header("Authorization", bearer(outsider)))
                .andExpect(status().isNotFound());

        String ceo = fx.token(mvc, fx.newUser(org, Role.LEADERSHIP));
        mvc.perform(put("/api/separations/" + id + "/clearance/" + itemId).header("Authorization", bearer(ceo))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"done\":true}"))
                .andExpect(status().isForbidden());
    }
}
