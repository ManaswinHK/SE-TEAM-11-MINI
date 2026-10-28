package com.aas.auth;

import static com.aas.TestFixtures.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aas.TestFixtures;
import com.aas.common.ApiException;
import com.aas.org.Department;
import com.aas.org.DepartmentRepository;
import com.aas.org.Organization;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoleBasedAccessTest {

    @Autowired MockMvc mvc;
    @Autowired TestFixtures fx;
    @Autowired DepartmentRepository departments;
    @Autowired AccessScope scope;

    Organization org;
    Department sales;
    Department finance;

    @BeforeEach
    void setUp() {
        org = fx.newOrg();
        sales = departments.save(new Department(org.getId(), "Sales", "Mumbai"));
        finance = departments.save(new Department(org.getId(), "Finance", "Pune"));
    }

    /** TC-AUTH-02: a Department Manager is limited to their own department. */
    @Test
    void departmentManagerIsScopedToOwnDepartment() {
        CurrentUser manager = new CurrentUser(1L, org.getId(), "m@x", "M", Role.DEPT_MANAGER, sales.getId());

        assertThat(scope.departmentFilter(manager, null)).isEqualTo(sales.getId());
        assertThat(scope.departmentFilter(manager, sales.getId())).isEqualTo(sales.getId());
        assertThatThrownBy(() -> scope.departmentFilter(manager, finance.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> scope.checkDepartment(manager, finance.getId())).isInstanceOf(ApiException.class);
    }

    @Test
    void organizationWideRolesKeepTheirFilter() {
        CurrentUser hr = new CurrentUser(1L, org.getId(), "h@x", "H", Role.HR_MANAGER, null);
        assertThat(scope.departmentFilter(hr, null)).isNull();
        assertThat(scope.departmentFilter(hr, finance.getId())).isEqualTo(finance.getId());
    }

    @Test
    void hrAdminCreatesUsersAndOtherRolesCannot() throws Exception {
        String admin = fx.token(mvc, fx.newUser(org, Role.HR_ADMIN));
        String email = "new-" + UUID.randomUUID() + "@test.local";

        mvc.perform(post("/api/users").header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"fullName":"Sales Lead","email":"%s","role":"DEPT_MANAGER","departmentId":%d,"password":"Secret123"}"""
                        .formatted(email, sales.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("DEPT_MANAGER"))
                .andExpect(jsonPath("$.departmentId").value(sales.getId()));

        mvc.perform(post("/api/users").header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"fullName":"No Dept","email":"x-%s","role":"DEPT_MANAGER","password":"Secret123"}""".formatted(email)))
                .andExpect(status().isBadRequest());

        for (Role role : new Role[] {Role.HR_MANAGER, Role.DEPT_MANAGER, Role.LEADERSHIP}) {
            AppUser u = fx.newUser(org, role);
            if (role == Role.DEPT_MANAGER) {
                u.setDepartmentId(sales.getId());
                fx.save(u);
            }
            mvc.perform(get("/api/users").header("Authorization", bearer(fx.token(mvc, u))))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void tokenCarriesRoleAndDepartment() throws Exception {
        AppUser manager = fx.newUser(org, Role.DEPT_MANAGER);
        manager.setDepartmentId(sales.getId());
        fx.save(manager);
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(fx.token(mvc, manager))))
                .andExpect(jsonPath("$.role").value("DEPT_MANAGER"))
                .andExpect(jsonPath("$.departmentId").value(sales.getId()));
    }
}
