package com.aas.alert;

import com.aas.analytics.AnalyticsService;
import com.aas.analytics.AnalyticsService.GroupRow;
import com.aas.auth.AccessScope;
import com.aas.auth.AppUser;
import com.aas.auth.AppUserRepository;
import com.aas.auth.CurrentUser;
import com.aas.auth.Role;
import com.aas.org.Department;
import com.aas.org.DepartmentRepository;
import com.aas.org.Organization;
import com.aas.org.OrganizationRepository;
import com.aas.survey.MailGateway;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily check of each department's rolling 3-month attrition against the organization's threshold (AAS-F-060).
 * One alert per breach goes in-app and by email to HR and that department's manager.
 */
@Service
public class AlertService {

    public record AlertView(Long id, Long departmentId, String departmentName, BigDecimal rate, BigDecimal threshold,
                            LocalDate periodFrom, LocalDate periodTo, LocalDateTime createdAt, boolean resolved) {
    }

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AttritionAlertRepository alerts;
    private final AnalyticsService analytics;
    private final OrganizationRepository orgs;
    private final DepartmentRepository departments;
    private final AppUserRepository users;
    private final MailGateway mail;
    private final AccessScope scope;

    public AlertService(AttritionAlertRepository alerts, AnalyticsService analytics, OrganizationRepository orgs,
                        DepartmentRepository departments, AppUserRepository users, MailGateway mail, AccessScope scope) {
        this.alerts = alerts;
        this.analytics = analytics;
        this.orgs = orgs;
        this.departments = departments;
        this.users = users;
        this.mail = mail;
        this.scope = scope;
    }

    @Scheduled(cron = "${aas.alert.cron:0 0 6 * * *}")
    public void checkAllOrganizations() {
        orgs.findAll().forEach(o -> check(o.getId(), LocalDate.now()));
    }

    /** Returns the alerts raised by this check. */
    @Transactional
    public List<AttritionAlert> check(Long orgId, LocalDate today) {
        Organization org = orgs.findById(orgId).orElseThrow();
        LocalDate from = today.minusMonths(3).plusDays(1);
        CurrentUser system = new CurrentUser(null, orgId, "system", "Daily alert check", Role.HR_ADMIN, null);
        Map<String, Department> byName = departments.findByOrgIdOrderByName(orgId).stream()
                .collect(Collectors.toMap(Department::getName, Function.identity(), (a, b) -> a));
        List<AttritionAlert> raised = new ArrayList<>();
        for (GroupRow row : analytics.attrition(system, from, today, null).byDepartment()) {
            Department dept = byName.get(row.key());
            if (dept == null) {
                continue;
            }
            Optional<AttritionAlert> open = alerts.findFirstByOrgIdAndDepartmentIdAndResolvedAtIsNull(orgId, dept.getId());
            boolean over = row.attritionRate().compareTo(org.getRiskThreshold()) > 0;
            if (!over) {
                open.ifPresent(AttritionAlert::resolve);
            } else if (open.isEmpty()) {
                AttritionAlert a = alerts.save(new AttritionAlert(orgId, dept.getId(), row.attritionRate(),
                        org.getRiskThreshold(), from, today));
                notify(org, dept, a);
                raised.add(a);
            }
        }
        return raised;
    }

    private void notify(Organization org, Department dept, AttritionAlert a) {
        String subject = "Attrition alert: " + dept.getName() + " at " + a.getRate() + "%";
        String body = """
                %s's attrition over the last 3 months (%s to %s) is %s%%, above the %s%% threshold set for %s.

                Open the AAS dashboard to see who left and why.
                """.formatted(dept.getName(), a.getPeriodFrom(), a.getPeriodTo(), a.getRate(), a.getThreshold(), org.getName());
        for (AppUser u : users.findByOrgIdOrderByFullName(org.getId())) {
            boolean hr = u.getRole() == Role.HR_ADMIN || u.getRole() == Role.HR_MANAGER;
            boolean deptManager = u.getRole() == Role.DEPT_MANAGER && dept.getId().equals(u.getDepartmentId());
            if (u.isActive() && (hr || deptManager)) {
                try {
                    mail.send(u.getEmail(), subject, body);
                } catch (RuntimeException ex) {
                    log.warn("Could not email alert {} to user {}: {}", a.getId(), u.getId(), ex.getMessage());
                }
            }
        }
    }

    public List<AlertView> recent(CurrentUser user) {
        Long dept = scope.departmentFilter(user, null);
        Map<Long, String> names = departments.findByOrgIdOrderByName(user.orgId()).stream()
                .collect(Collectors.toMap(Department::getId, Department::getName));
        return alerts.findTop50ByOrgIdOrderByCreatedAtDesc(user.orgId()).stream()
                .filter(a -> dept == null || dept.equals(a.getDepartmentId()))
                .map(a -> new AlertView(a.getId(), a.getDepartmentId(), names.get(a.getDepartmentId()), a.getRate(),
                        a.getThreshold(), a.getPeriodFrom(), a.getPeriodTo(), a.getCreatedAt(), a.getResolvedAt() != null))
                .toList();
    }
}
