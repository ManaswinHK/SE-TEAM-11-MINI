package com.aas.analytics;

import com.aas.auth.AccessScope;
import com.aas.auth.CurrentUser;
import com.aas.common.ApiException;
import com.aas.employee.Employee;
import com.aas.employee.EmployeeRepository;
import com.aas.org.Department;
import com.aas.org.DepartmentRepository;
import com.aas.separation.Separation;
import com.aas.separation.SeparationRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Attrition KPIs (AAS-F-040) and breakdowns by department, tenure band and location (AAS-F-041).
 * A separation counts in a period when the person's last working day falls in it and has already passed.
 */
@Service
public class AnalyticsService {

    public record Kpis(LocalDate from, LocalDate to, int openingHeadcount, int closingHeadcount, BigDecimal averageHeadcount,
                       long separations, long voluntary, long regretted, BigDecimal attritionRate,
                       BigDecimal voluntaryRate, BigDecimal regrettedRate) {
    }

    public record GroupRow(String key, long separations, BigDecimal averageHeadcount, BigDecimal attritionRate) {
    }

    public record BandRow(String band, long separations, BigDecimal share) {
    }

    public record AttritionReport(Kpis kpis, Long departmentId, List<GroupRow> byDepartment, List<BandRow> byTenure,
                                  List<GroupRow> byLocation) {
    }

    static final List<String> BANDS = List.of("< 1 year", "1-3 years", "3-5 years", "> 5 years");

    private final EmployeeRepository employees;
    private final SeparationRepository separations;
    private final DepartmentRepository departments;
    private final AccessScope scope;

    public AnalyticsService(EmployeeRepository employees, SeparationRepository separations,
                            DepartmentRepository departments, AccessScope scope) {
        this.employees = employees;
        this.separations = separations;
        this.departments = departments;
        this.scope = scope;
    }

    public AttritionReport attrition(CurrentUser user, LocalDate from, LocalDate to, Long departmentId) {
        if (to.isBefore(from)) {
            throw ApiException.badRequest("'to' must not be before 'from'");
        }
        Long dept = scope.departmentFilter(user, departmentId);
        LocalDate today = LocalDate.now();
        LocalDate effectiveTo = to.isAfter(today) ? today : to;

        List<Employee> people = employees.findByOrgId(user.orgId()).stream()
                .filter(e -> dept == null || dept.equals(e.getDepartmentId()))
                .toList();
        Map<Long, Employee> byId = people.stream().collect(Collectors.toMap(Employee::getId, Function.identity()));
        List<Separation> exits = effectiveTo.isBefore(from) ? List.of()
                : separations.findByOrgIdAndLastWorkingDayBetweenOrderByLastWorkingDayDesc(user.orgId(), from, effectiveTo)
                        .stream().filter(s -> byId.containsKey(s.getEmployeeId())).toList();

        Kpis kpis = kpis(from, effectiveTo, people, exits);

        Map<Long, String> deptNames = departments.findByOrgIdOrderByName(user.orgId()).stream()
                .collect(Collectors.toMap(Department::getId, Department::getName));
        List<GroupRow> byDepartment = groups(people, exits, byId, e -> deptNames.get(e.getDepartmentId()), from, effectiveTo);
        List<GroupRow> byLocation = groups(people, exits, byId,
                e -> e.getLocation() == null ? "Not set" : e.getLocation(), from, effectiveTo);

        Map<String, Long> bandCounts = new LinkedHashMap<>();
        BANDS.forEach(b -> bandCounts.put(b, 0L));
        for (Separation s : exits) {
            Employee e = byId.get(s.getEmployeeId());
            int months = (int) Period.between(e.getJoinDate(), s.getLastWorkingDay()).toTotalMonths();
            bandCounts.merge(AttritionCalculator.tenureBand(Math.max(months, 0)), 1L, Long::sum);
        }
        List<BandRow> byTenure = bandCounts.entrySet().stream()
                .map(en -> new BandRow(en.getKey(), en.getValue(),
                        exits.isEmpty() ? BigDecimal.ZERO.setScale(2) :
                                AttritionCalculator.rate(en.getValue(), BigDecimal.valueOf(exits.size()))))
                .toList();

        return new AttritionReport(kpis, dept, byDepartment, byTenure, byLocation);
    }

    private static Kpis kpis(LocalDate from, LocalDate to, List<Employee> people, List<Separation> exits) {
        int opening = (int) people.stream().filter(e -> e.isEmployedOn(from)).count();
        int closing = (int) people.stream().filter(e -> e.isEmployedOn(to)).count();
        BigDecimal avg = AttritionCalculator.averageHeadcount(opening, closing);
        long voluntary = exits.stream().filter(s -> s.getType() == Separation.Type.VOLUNTARY).count();
        long regretted = exits.stream().filter(Separation::isRegretted).count();
        return new Kpis(from, to, opening, closing, avg, exits.size(), voluntary, regretted,
                AttritionCalculator.rate(exits.size(), avg), AttritionCalculator.rate(voluntary, avg),
                AttritionCalculator.rate(regretted, avg));
    }

    private static List<GroupRow> groups(List<Employee> people, List<Separation> exits, Map<Long, Employee> byId,
                                         Function<Employee, String> key, LocalDate from, LocalDate to) {
        Map<String, List<Employee>> grouped = people.stream()
                .filter(e -> key.apply(e) != null)
                .collect(Collectors.groupingBy(key));
        List<GroupRow> rows = new ArrayList<>();
        for (Map.Entry<String, List<Employee>> g : grouped.entrySet()) {
            Predicate<Separation> inGroup = s -> Objects.equals(key.apply(byId.get(s.getEmployeeId())), g.getKey());
            long count = exits.stream().filter(inGroup).count();
            int opening = (int) g.getValue().stream().filter(e -> e.isEmployedOn(from)).count();
            int closing = (int) g.getValue().stream().filter(e -> e.isEmployedOn(to)).count();
            BigDecimal avg = AttritionCalculator.averageHeadcount(opening, closing);
            if (count > 0 || avg.signum() > 0) {
                rows.add(new GroupRow(g.getKey(), count, avg, AttritionCalculator.rate(count, avg)));
            }
        }
        rows.sort(Comparator.comparing(GroupRow::attritionRate).reversed().thenComparing(GroupRow::key));
        return rows;
    }
}
