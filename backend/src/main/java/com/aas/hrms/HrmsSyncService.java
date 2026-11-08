package com.aas.hrms;

import com.aas.auth.AppUser;
import com.aas.auth.AppUserRepository;
import com.aas.auth.Role;
import com.aas.common.AuditLog;
import com.aas.common.AuditLogRepository;
import com.aas.employee.Employee;
import com.aas.employee.EmployeeRepository;
import com.aas.hrms.HrmsClient.HrmsRecord;
import com.aas.org.Department;
import com.aas.org.DepartmentRepository;
import com.aas.org.Organization;
import com.aas.org.OrganizationRepository;
import com.aas.survey.MailGateway;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Nightly employee master-data sync from the HRMS (AAS-F-012). Every run is logged; a failed run is also
 * emailed to the HR Admins.
 */
@Service
public class HrmsSyncService {

    private static final Logger log = LoggerFactory.getLogger(HrmsSyncService.class);

    private final HrmsClient hrms;
    private final HrmsSyncLogRepository logs;
    private final EmployeeRepository employees;
    private final DepartmentRepository departments;
    private final OrganizationRepository orgs;
    private final AppUserRepository users;
    private final AuditLogRepository audit;
    private final MailGateway mail;
    private final TransactionTemplate tx;

    public HrmsSyncService(HrmsClient hrms, HrmsSyncLogRepository logs, EmployeeRepository employees,
                           DepartmentRepository departments, OrganizationRepository orgs, AppUserRepository users,
                           AuditLogRepository audit, MailGateway mail, TransactionTemplate tx) {
        this.hrms = hrms;
        this.logs = logs;
        this.employees = employees;
        this.departments = departments;
        this.orgs = orgs;
        this.users = users;
        this.audit = audit;
        this.mail = mail;
        this.tx = tx;
    }

    @Scheduled(cron = "${aas.hrms.cron:0 0 1 * * *}")
    public void syncAllOrganizations() {
        orgs.findAll().forEach(o -> sync(o.getId()));
    }

    /** Applies the HRMS records in one transaction; if anything goes wrong nothing is changed and the run is FAILED. */
    public HrmsSyncLog sync(Long orgId) {
        HrmsSyncLog run = logs.save(new HrmsSyncLog(orgId));
        try {
            Outcome o = tx.execute(status -> apply(orgId, hrms.fetchEmployees(orgId)));
            run.finish(HrmsSyncLog.Status.SUCCESS, o.created(), o.updated(), o.skipped().size(),
                    o.skipped().isEmpty() ? null : String.join("; ", o.skipped()));
        } catch (RuntimeException ex) {
            log.warn("HRMS sync failed for organization {}: {}", orgId, ex.getMessage());
            run.finish(HrmsSyncLog.Status.FAILED, 0, 0, 0, ex.getMessage());
            alertAdmins(orgId, ex.getMessage());
        }
        return logs.save(run);
    }

    private record Outcome(int created, int updated, List<String> skipped) {
    }

    private Outcome apply(Long orgId, List<HrmsRecord> records) {
        Map<String, Department> deptByName = departments.findByOrgIdOrderByName(orgId).stream()
                .collect(Collectors.toMap(d -> d.getName().toLowerCase(Locale.ROOT), Function.identity(), (a, b) -> a));
        Map<String, Employee> byCode = employees.findByOrgId(orgId).stream()
                .collect(Collectors.toMap(e -> e.getEmployeeCode().toLowerCase(Locale.ROOT), Function.identity()));
        int created = 0;
        int updated = 0;
        List<String> errors = new ArrayList<>();
        for (HrmsRecord r : records) {
            Department dept = r.department() == null ? null : deptByName.get(r.department().toLowerCase(Locale.ROOT));
            if (r.employeeCode() == null || r.employeeCode().isBlank() || dept == null) {
                errors.add((r.employeeCode() == null ? "?" : r.employeeCode()) + ": unknown department '" + r.department() + "'");
                continue;
            }
            Employee e = byCode.get(r.employeeCode().toLowerCase(Locale.ROOT));
            if (e == null) {
                if (r.fullName() == null || r.joinDate() == null) {
                    errors.add(r.employeeCode() + ": new employee needs a name and join date");
                    continue;
                }
                e = new Employee(orgId, r.employeeCode(), r.fullName(), dept.getId(), r.joinDate());
                copy(r, dept, e);
                employees.save(e);
                created++;
            } else if (copy(r, dept, e)) {
                updated++;
            }
        }
        audit.save(new AuditLog(orgId, null, "hrms-sync", "SYNC", "EMPLOYEE", null,
                created + " created, " + updated + " updated, " + errors.size() + " skipped"));
        return new Outcome(created, updated, errors);
    }

    /** Copies the HRMS fields onto the employee; returns whether anything changed. */
    private static boolean copy(HrmsRecord r, Department dept, Employee e) {
        boolean changed = false;
        if (r.fullName() != null && !r.fullName().equals(e.getFullName())) { e.setFullName(r.fullName()); changed = true; }
        if (!Objects.equals(r.email(), e.getEmail()) && r.email() != null) { e.setEmail(r.email()); changed = true; }
        if (!dept.getId().equals(e.getDepartmentId())) { e.setDepartmentId(dept.getId()); changed = true; }
        if (r.designation() != null && !r.designation().equals(e.getDesignation())) { e.setDesignation(r.designation()); changed = true; }
        if (r.managerName() != null && !r.managerName().equals(e.getManagerName())) { e.setManagerName(r.managerName()); changed = true; }
        if (r.location() != null && !r.location().equals(e.getLocation())) { e.setLocation(r.location()); changed = true; }
        if (r.grade() != null && !r.grade().equals(e.getGrade())) { e.setGrade(r.grade()); changed = true; }
        return changed;
    }

    private void alertAdmins(Long orgId, String reason) {
        String org = orgs.findById(orgId).map(Organization::getName).orElse("your organization");
        for (AppUser u : users.findByOrgIdOrderByFullName(orgId)) {
            if (u.isActive() && u.getRole() == Role.HR_ADMIN) {
                try {
                    mail.send(u.getEmail(), "HRMS sync failed", "Last night's HRMS sync for " + org + " failed: " + reason
                            + "\n\nNo employee records were changed. The next run will try again.");
                } catch (RuntimeException ex) {
                    log.warn("Could not email sync failure to user {}", u.getId());
                }
            }
        }
    }

    public List<HrmsSyncLog> recent(Long orgId) {
        return logs.findTop20ByOrgIdOrderByStartedAtDesc(orgId);
    }
}
