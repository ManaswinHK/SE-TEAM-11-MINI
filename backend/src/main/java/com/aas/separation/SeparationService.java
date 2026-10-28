package com.aas.separation;

import com.aas.auth.AccessScope;
import com.aas.auth.CurrentUser;
import com.aas.common.ApiException;
import com.aas.common.AuditLog;
import com.aas.common.AuditLogRepository;
import com.aas.common.FieldValidationException;
import com.aas.employee.Employee;
import com.aas.employee.EmployeeRepository;
import com.aas.employee.EmployeeStatus;
import com.aas.org.Department;
import com.aas.org.DepartmentRepository;
import com.aas.org.ExitReason;
import com.aas.org.ExitReasonRepository;
import com.aas.separation.SeparationDtos.SeparationRequest;
import com.aas.separation.SeparationDtos.SeparationView;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Recording employees who leave (AAS-F-020) and the rules that keep that data valid (AAS-F-021). */
@Service
public class SeparationService {

    private static final Logger log = LoggerFactory.getLogger(SeparationService.class);

    private final SeparationRepository separations;
    private final EmployeeRepository employees;
    private final ExitReasonRepository reasons;
    private final DepartmentRepository departments;
    private final AuditLogRepository audit;
    private final AccessScope scope;

    public SeparationService(SeparationRepository separations, EmployeeRepository employees, ExitReasonRepository reasons,
                             DepartmentRepository departments, AuditLogRepository audit, AccessScope scope) {
        this.separations = separations;
        this.employees = employees;
        this.reasons = reasons;
        this.departments = departments;
        this.audit = audit;
        this.scope = scope;
    }

    @Transactional
    public SeparationView record(CurrentUser user, SeparationRequest req) {
        Employee emp = employees.findByIdAndOrgId(req.employeeId(), user.orgId())
                .orElseThrow(() -> ApiException.notFound("Employee"));
        if (emp.getStatus() == EmployeeStatus.NOTICE_PERIOD || emp.getStatus() == EmployeeStatus.SEPARATED) {
            throw ApiException.conflict(emp.getFullName() + " already has a recorded separation");
        }
        if (emp.getStatus() == EmployeeStatus.INACTIVE) {
            throw ApiException.conflict("This employee record is deactivated");
        }
        if (req.lastWorkingDay().isBefore(req.resignationDate())) {
            throw new FieldValidationException("lastWorkingDay", "Last working day cannot be before the resignation date");
        }
        if (req.resignationDate().isBefore(emp.getJoinDate())) {
            throw new FieldValidationException("resignationDate", "Resignation date cannot be before the join date ("
                    + emp.getJoinDate() + ")");
        }
        if (req.resignationDate().isAfter(LocalDate.now())) {
            throw new FieldValidationException("resignationDate", "Resignation date cannot be in the future");
        }
        ExitReason reason = reasons.findByIdAndOrgId(req.reasonId(), user.orgId())
                .filter(ExitReason::isActive)
                .orElseThrow(() -> new FieldValidationException("reasonId", "Choose a valid exit reason"));

        Separation s = separations.save(new Separation(user.orgId(), emp.getId(), req.resignationDate(), req.lastWorkingDay(),
                req.type(), reason.getId(), req.regretted(), blankToNull(req.remarks()), user.userId()));
        emp.setLastWorkingDay(req.lastWorkingDay());
        emp.setStatus(s.getStatus() == Separation.Status.COMPLETED ? EmployeeStatus.SEPARATED : EmployeeStatus.NOTICE_PERIOD);

        audit.save(new AuditLog(user.orgId(), user.userId(), user.email(), "CREATE", "SEPARATION", s.getId(),
                "Employee " + emp.getEmployeeCode() + ", " + req.type() + ", last working day " + req.lastWorkingDay()));
        return view(s, emp, departments.findByIdAndOrgId(emp.getDepartmentId(), user.orgId()).map(Department::getName).orElse(null),
                reason.getName());
    }

    public List<SeparationView> list(CurrentUser user, LocalDate from, LocalDate to, Long departmentId, Separation.Type type) {
        Long dept = scope.departmentFilter(user, departmentId);
        Map<Long, Employee> emps = employees.findByOrgId(user.orgId()).stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity()));
        Map<Long, String> deptNames = departments.findByOrgIdOrderByName(user.orgId()).stream()
                .collect(Collectors.toMap(Department::getId, Department::getName));
        Map<Long, String> reasonNames = reasons.findByOrgIdOrderByName(user.orgId()).stream()
                .collect(Collectors.toMap(ExitReason::getId, ExitReason::getName));
        return separations.findByOrgIdAndLastWorkingDayBetweenOrderByLastWorkingDayDesc(user.orgId(), from, to).stream()
                .filter(s -> type == null || s.getType() == type)
                .filter(s -> dept == null || dept.equals(emps.get(s.getEmployeeId()).getDepartmentId()))
                .map(s -> {
                    Employee e = emps.get(s.getEmployeeId());
                    return view(s, e, deptNames.get(e.getDepartmentId()), reasonNames.get(s.getReasonId()));
                })
                .toList();
    }

    public SeparationView get(CurrentUser user, Long id) {
        Separation s = separations.findByIdAndOrgId(id, user.orgId()).orElseThrow(() -> ApiException.notFound("Separation"));
        Employee e = employees.findByIdAndOrgId(s.getEmployeeId(), user.orgId()).orElseThrow();
        scope.checkDepartment(user, e.getDepartmentId());
        return view(s, e, departments.findById(e.getDepartmentId()).map(Department::getName).orElse(null),
                reasons.findById(s.getReasonId()).map(ExitReason::getName).orElse(null));
    }

    /** Daily: people whose last working day has passed move from notice period to separated. */
    @Scheduled(cron = "${aas.separation.complete-cron:0 10 0 * * *}")
    @Transactional
    public void completeDueSeparations() {
        completeDueSeparations(LocalDate.now());
    }

    @Transactional
    public int completeDueSeparations(LocalDate today) {
        List<Separation> due = separations.findByStatusAndLastWorkingDayBefore(Separation.Status.NOTICE_PERIOD, today);
        for (Separation s : due) {
            s.setStatus(Separation.Status.COMPLETED);
            employees.findById(s.getEmployeeId()).ifPresent(e -> e.setStatus(EmployeeStatus.SEPARATED));
        }
        if (!due.isEmpty()) {
            log.info("Completed {} separations whose last working day has passed", due.size());
        }
        return due.size();
    }

    static SeparationView view(Separation s, Employee e, String departmentName, String reasonName) {
        int tenure = (int) Period.between(e.getJoinDate(), s.getLastWorkingDay()).toTotalMonths();
        return new SeparationView(s.getId(), e.getId(), e.getEmployeeCode(), e.getFullName(), e.getDepartmentId(),
                departmentName, e.getJoinDate(), s.getResignationDate(), s.getLastWorkingDay(), Math.max(tenure, 0),
                s.getType(), s.getReasonId(), reasonName, s.isRegretted(), s.getStatus(), s.getRemarks());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
