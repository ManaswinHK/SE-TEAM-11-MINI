package com.aas.risk;

import com.aas.auth.AccessScope;
import com.aas.auth.CurrentUser;
import com.aas.common.ApiException;
import com.aas.common.AuditLog;
import com.aas.common.AuditLogRepository;
import com.aas.common.FieldValidationException;
import com.aas.employee.Employee;
import com.aas.employee.EmployeeRepository;
import com.aas.org.Department;
import com.aas.org.DepartmentRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Retention actions for at-risk employees (AAS-F-052). Department Managers work within their own team. */
@Service
public class RetentionService {

    public record NewAction(@NotNull Long employeeId, @NotBlank @Size(max = 200) String action,
                            @NotBlank @Size(max = 150) String ownerName, @NotNull LocalDate dueDate) {
    }

    public record ActionView(Long id, Long employeeId, String employeeName, String departmentName, String action,
                             String ownerName, LocalDate dueDate, RetentionAction.Status status, String outcome,
                             boolean overdue) {
    }

    private final RetentionActionRepository actions;
    private final EmployeeRepository employees;
    private final DepartmentRepository departments;
    private final AuditLogRepository audit;
    private final AccessScope scope;

    public RetentionService(RetentionActionRepository actions, EmployeeRepository employees,
                            DepartmentRepository departments, AuditLogRepository audit, AccessScope scope) {
        this.actions = actions;
        this.employees = employees;
        this.departments = departments;
        this.audit = audit;
        this.scope = scope;
    }

    @Transactional
    public ActionView log(CurrentUser user, NewAction req) {
        Employee e = employees.findByIdAndOrgId(req.employeeId(), user.orgId())
                .orElseThrow(() -> ApiException.notFound("Employee"));
        scope.checkDepartment(user, e.getDepartmentId());
        if (req.dueDate().isBefore(LocalDate.now())) {
            throw new FieldValidationException("dueDate", "Due date cannot be in the past");
        }
        RetentionAction a = actions.save(new RetentionAction(user.orgId(), e.getId(), req.action().trim(),
                req.ownerName().trim(), req.dueDate(), user.userId()));
        audit.save(new AuditLog(user.orgId(), user.userId(), user.email(), "CREATE", "RETENTION_ACTION", a.getId(),
                "Employee " + e.getEmployeeCode() + ": " + a.getAction()));
        return list(user, e.getId(), LocalDate.now()).stream().filter(v -> v.id().equals(a.getId())).findFirst().orElseThrow();
    }

    @Transactional
    public ActionView complete(CurrentUser user, Long id, String outcome) {
        RetentionAction a = actions.findByIdAndOrgId(id, user.orgId()).orElseThrow(() -> ApiException.notFound("Retention action"));
        Employee e = employees.findByIdAndOrgId(a.getEmployeeId(), user.orgId()).orElseThrow();
        scope.checkDepartment(user, e.getDepartmentId());
        if (a.getStatus() == RetentionAction.Status.DONE) {
            throw ApiException.conflict("This action is already done");
        }
        a.complete(outcome == null || outcome.isBlank() ? null : outcome.trim());
        audit.save(new AuditLog(user.orgId(), user.userId(), user.email(), "COMPLETE", "RETENTION_ACTION", a.getId(), a.getOutcome()));
        return list(user, e.getId(), LocalDate.now()).stream().filter(v -> v.id().equals(id)).findFirst().orElseThrow();
    }

    /** All actions in scope, or one employee's; open overdue ones are flagged against {@code today}. */
    public List<ActionView> list(CurrentUser user, Long employeeId, LocalDate today) {
        Long dept = scope.departmentFilter(user, null);
        Map<Long, Employee> byId = employees.findByOrgId(user.orgId()).stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity()));
        Map<Long, String> deptNames = departments.findByOrgIdOrderByName(user.orgId()).stream()
                .collect(Collectors.toMap(Department::getId, Department::getName));
        return actions.findByOrgIdOrderByDueDate(user.orgId()).stream()
                .filter(a -> employeeId == null || employeeId.equals(a.getEmployeeId()))
                .filter(a -> dept == null || dept.equals(byId.get(a.getEmployeeId()).getDepartmentId()))
                .map(a -> {
                    Employee e = byId.get(a.getEmployeeId());
                    return new ActionView(a.getId(), e.getId(), e.getFullName(), deptNames.get(e.getDepartmentId()),
                            a.getAction(), a.getOwnerName(), a.getDueDate(), a.getStatus(), a.getOutcome(), a.isOverdue(today));
                })
                .toList();
    }
}
