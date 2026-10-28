package com.aas.auth;

import com.aas.common.ApiException;
import com.aas.org.DepartmentRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** HR Admins create user accounts and assign roles (AAS-F-002). */
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('HR_ADMIN')")
public class UserController {

    public record UserRow(Long id, String email, String fullName, Role role, Long departmentId, boolean active, boolean mfaEnabled) {
        static UserRow of(AppUser u) {
            return new UserRow(u.getId(), u.getEmail(), u.getFullName(), u.getRole(), u.getDepartmentId(), u.isActive(), u.isMfaEnabled());
        }
    }

    public record CreateUserRequest(@NotBlank @Size(max = 150) String fullName,
                                    @NotBlank @Email String email,
                                    @NotNull Role role,
                                    Long departmentId,
                                    @NotBlank @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
                                            message = "must be at least 8 characters with letters and digits") String password) {
    }

    public record UpdateUserRequest(@NotBlank @Size(max = 150) String fullName, @NotNull Role role, Long departmentId, Boolean active) {
    }

    private final AppUserRepository users;
    private final DepartmentRepository departments;
    private final PasswordEncoder encoder;

    public UserController(AppUserRepository users, DepartmentRepository departments, PasswordEncoder encoder) {
        this.users = users;
        this.departments = departments;
        this.encoder = encoder;
    }

    @GetMapping
    public List<UserRow> list() {
        return users.findByOrgIdOrderByFullName(CurrentUser.get().orgId()).stream().map(UserRow::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public UserRow create(@Valid @RequestBody CreateUserRequest req) {
        Long orgId = CurrentUser.get().orgId();
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw ApiException.conflict("An account with this email already exists");
        }
        AppUser user = new AppUser(orgId, req.email().trim(), req.fullName().trim(), encoder.encode(req.password()), req.role());
        user.setDepartmentId(validDepartment(orgId, req.role(), req.departmentId()));
        return UserRow.of(users.save(user));
    }

    @PutMapping("/{id}")
    @Transactional
    public UserRow update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest req) {
        CurrentUser me = CurrentUser.get();
        AppUser user = users.findByIdAndOrgId(id, me.orgId()).orElseThrow(() -> ApiException.notFound("User"));
        if (user.getId().equals(me.userId()) && (req.role() != Role.HR_ADMIN || Boolean.FALSE.equals(req.active()))) {
            throw ApiException.badRequest("You cannot remove your own HR Admin access");
        }
        user.setFullName(req.fullName().trim());
        user.setRole(req.role());
        user.setDepartmentId(validDepartment(me.orgId(), req.role(), req.departmentId()));
        if (req.active() != null) {
            user.setActive(req.active());
        }
        return UserRow.of(user);
    }

    private Long validDepartment(Long orgId, Role role, Long departmentId) {
        if (role == Role.DEPT_MANAGER && departmentId == null) {
            throw ApiException.badRequest("A Department Manager must be linked to a department");
        }
        if (departmentId != null && departments.findByIdAndOrgId(departmentId, orgId).isEmpty()) {
            throw ApiException.notFound("Department");
        }
        return departmentId;
    }
}
