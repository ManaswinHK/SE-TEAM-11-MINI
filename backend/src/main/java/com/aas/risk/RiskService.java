package com.aas.risk;

import com.aas.analytics.AttritionCalculator;
import com.aas.auth.AccessScope;
import com.aas.auth.CurrentUser;
import com.aas.employee.Employee;
import com.aas.employee.EmployeeRepository;
import com.aas.employee.EmployeeStatus;
import com.aas.org.Department;
import com.aas.org.DepartmentRepository;
import com.aas.org.Organization;
import com.aas.org.OrganizationRepository;
import com.aas.separation.Separation;
import com.aas.separation.SeparationRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Scores every active employee once a week (AAS-F-050) and serves the latest scores. */
@Service
public class RiskService {

    /** share: the factor's part of the score in percent, so the card can show relative contribution. */
    public record FactorView(String label, int points, int share) {
    }

    public record RiskView(Long employeeId, String employeeCode, String employeeName, Long departmentId,
                           String departmentName, int score, RiskModel.Band band, LocalDate scoredOn,
                           List<FactorView> factors) {
    }

    private static final Logger log = LoggerFactory.getLogger(RiskService.class);

    private final RiskScoreRepository scores;
    private final EmployeeRepository employees;
    private final SeparationRepository separations;
    private final DepartmentRepository departments;
    private final OrganizationRepository orgs;
    private final AccessScope scope;

    public RiskService(RiskScoreRepository scores, EmployeeRepository employees, SeparationRepository separations,
                       DepartmentRepository departments, OrganizationRepository orgs, AccessScope scope) {
        this.scores = scores;
        this.employees = employees;
        this.separations = separations;
        this.departments = departments;
        this.orgs = orgs;
        this.scope = scope;
    }

    /** Every Monday at 02:00. */
    @Scheduled(cron = "${aas.risk.cron:0 0 2 * * MON}")
    public void scoreAllOrganizations() {
        for (Organization org : orgs.findAll()) {
            int n = scoreOrganization(org.getId(), LocalDate.now());
            log.info("Scored {} employees for organization {}", n, org.getId());
        }
    }

    /** Replaces any scores already taken today, so running twice in a day is harmless. */
    @Transactional
    public int scoreOrganization(Long orgId, LocalDate today) {
        List<Employee> people = employees.findByOrgId(orgId);
        Map<Long, BigDecimal> teamRates = teamAttritionRates(orgId, people, today);
        scores.deleteRun(orgId, today);
        int count = 0;
        for (Employee e : people) {
            if (e.getStatus() != EmployeeStatus.ACTIVE) {
                continue;
            }
            RiskModel.Result r = RiskModel.score(e.getJoinDate(), e.getLastPromotionDate(), e.getPerformanceRating(),
                    teamRates.getOrDefault(e.getDepartmentId(), BigDecimal.ZERO), today);
            scores.save(new RiskScore(orgId, e.getId(), r, today));
            count++;
        }
        return count;
    }

    /** Each department's attrition rate over the 12 months to {@code today}. */
    private Map<Long, BigDecimal> teamAttritionRates(Long orgId, List<Employee> people, LocalDate today) {
        LocalDate from = today.minusMonths(12);
        Map<Long, Long> exits = separations.findByOrgIdAndLastWorkingDayBetweenOrderByLastWorkingDayDesc(orgId, from, today)
                .stream().filter(Separation::countsAsAttrition)
                .map(s -> people.stream().filter(p -> p.getId().equals(s.getEmployeeId())).findFirst().orElse(null))
                .filter(p -> p != null)
                .collect(Collectors.groupingBy(Employee::getDepartmentId, Collectors.counting()));
        Map<Long, BigDecimal> rates = new HashMap<>();
        people.stream().collect(Collectors.groupingBy(Employee::getDepartmentId)).forEach((dept, members) -> {
            int opening = (int) members.stream().filter(m -> m.isEmployedOn(from)).count();
            int closing = (int) members.stream().filter(m -> m.isEmployedOn(today)).count();
            rates.put(dept, AttritionCalculator.rate(exits.getOrDefault(dept, 0L), AttritionCalculator.averageHeadcount(opening, closing)));
        });
        return rates;
    }

    /** The latest run, highest risk first. Department Managers see their own department only. */
    public List<RiskView> latest(CurrentUser user, RiskModel.Band band, Long departmentId) {
        Long dept = scope.departmentFilter(user, departmentId);
        LocalDate run = scores.latestRun(user.orgId()).orElse(null);
        if (run == null) {
            return List.of();
        }
        Map<Long, Employee> byId = employees.findByOrgId(user.orgId()).stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity()));
        Map<Long, String> deptNames = departments.findByOrgIdOrderByName(user.orgId()).stream()
                .collect(Collectors.toMap(Department::getId, Department::getName));
        return scores.findByOrgIdAndScoredOn(user.orgId(), run).stream()
                .filter(r -> band == null || r.getBand() == band)
                .map(r -> {
                    Employee e = byId.get(r.getEmployeeId());
                    List<FactorView> factors = r.topFactors().stream()
                            .map(f -> new FactorView(f.label(), f.points(), Math.round(100f * f.points() / r.getScore())))
                            .toList();
                    return new RiskView(e.getId(), e.getEmployeeCode(), e.getFullName(), e.getDepartmentId(),
                            deptNames.get(e.getDepartmentId()), r.getScore(), r.getBand(), r.getScoredOn(), factors);
                })
                .filter(v -> dept == null || dept.equals(v.departmentId()))
                .sorted(Comparator.comparingInt(RiskView::score).reversed().thenComparing(RiskView::employeeName))
                .toList();
    }
}
