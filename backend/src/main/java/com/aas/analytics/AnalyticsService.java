package com.aas.analytics;

import com.aas.auth.AccessScope;
import com.aas.auth.CurrentUser;
import com.aas.auth.Role;
import com.aas.common.ApiException;
import com.aas.employee.Employee;
import com.aas.employee.EmployeeRepository;
import com.aas.org.Department;
import com.aas.org.DepartmentRepository;
import com.aas.org.ExitReason;
import com.aas.org.ExitReasonRepository;
import com.aas.separation.Separation;
import com.aas.separation.SeparationRepository;
import com.aas.survey.SurveyResponse;
import com.aas.survey.SurveyResponseRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Attrition KPIs (AAS-F-040) and breakdowns by department, tenure band and location (AAS-F-041).
 * A separation counts in a period when the person's last working day falls in it and has already passed.
 */
@Service
public class AnalyticsService {

    /** earlyLeavers: people who left within their first 12 months (AAS-F-044). */
    public record Kpis(LocalDate from, LocalDate to, int openingHeadcount, int closingHeadcount, BigDecimal averageHeadcount,
                       long separations, long voluntary, long regretted, long earlyLeavers, BigDecimal attritionRate,
                       BigDecimal voluntaryRate, BigDecimal regrettedRate, BigDecimal earlyAttritionRate,
                       BigDecimal earlyLeaverShare) {
    }

    public record GroupRow(String key, long separations, BigDecimal averageHeadcount, BigDecimal attritionRate) {
    }

    public record BandRow(String band, long separations, BigDecimal share) {
    }

    public record AttritionReport(Kpis kpis, Long departmentId, List<GroupRow> byDepartment, List<BandRow> byTenure,
                                  List<GroupRow> byLocation) {
    }

    /** One calendar month of the trend chart, with the same month a year earlier for comparison (AAS-F-042). */
    public record TrendPoint(String month, long separations, BigDecimal averageHeadcount, BigDecimal attritionRate,
                             long previousYearSeparations, BigDecimal previousYearRate) {
    }

    public record ReasonRow(String reason, ExitReason.Category category, long separations, BigDecimal share) {
    }

    /** Average 1-5 ratings. The averages are null when {@code insufficientData} is set (AAS-SR-006). */
    public record SurveyScores(long responses, boolean insufficientData, BigDecimal manager, BigDecimal pay,
                               BigDecimal growth, BigDecimal workLife, BigDecimal culture) {
    }

    public record ExitInsights(List<ReasonRow> reasons, SurveyScores survey) {
    }

    /** Below this many responses Department Managers see no scores, so individuals cannot be identified. */
    static final int MIN_SURVEY_RESPONSES = 5;

    static final List<String> BANDS = List.of("< 1 year", "1-3 years", "3-5 years", "> 5 years");

    private final EmployeeRepository employees;
    private final SeparationRepository separations;
    private final DepartmentRepository departments;
    private final AccessScope scope;
    private final ExitReasonRepository reasons;
    private final SurveyResponseRepository surveyResponses;

    public AnalyticsService(EmployeeRepository employees, SeparationRepository separations,
                            DepartmentRepository departments, AccessScope scope, ExitReasonRepository reasons,
                            SurveyResponseRepository surveyResponses) {
        this.reasons = reasons;
        this.surveyResponses = surveyResponses;
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
                        .stream().filter(Separation::countsAsAttrition)
                        .filter(s -> byId.containsKey(s.getEmployeeId())).toList();

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

    /** Monthly attrition rate for the last {@code months} calendar months (the current month runs to today). */
    public List<TrendPoint> trend(CurrentUser user, int months, Long departmentId) {
        if (months < 1 || months > 24) {
            throw ApiException.badRequest("months must be between 1 and 24");
        }
        Long dept = scope.departmentFilter(user, departmentId);
        LocalDate today = LocalDate.now();
        YearMonth last = YearMonth.from(today);
        YearMonth first = last.minusMonths(months - 1);

        List<Employee> people = employees.findByOrgId(user.orgId()).stream()
                .filter(e -> dept == null || dept.equals(e.getDepartmentId()))
                .toList();
        Map<Long, Employee> byId = people.stream().collect(Collectors.toMap(Employee::getId, Function.identity()));
        List<Separation> exits = separations.findByOrgIdAndLastWorkingDayBetweenOrderByLastWorkingDayDesc(
                        user.orgId(), first.minusYears(1).atDay(1), today).stream()
                .filter(Separation::countsAsAttrition)
                .filter(s -> byId.containsKey(s.getEmployeeId()))
                .toList();

        List<TrendPoint> points = new ArrayList<>();
        for (YearMonth m = first; !m.isAfter(last); m = m.plusMonths(1)) {
            Kpis now = monthKpis(m, today, people, exits);
            Kpis before = monthKpis(m.minusYears(1), today, people, exits);
            points.add(new TrendPoint(m.toString(), now.separations(), now.averageHeadcount(), now.attritionRate(),
                    before.separations(), before.attritionRate()));
        }
        return points;
    }

    private static Kpis monthKpis(YearMonth month, LocalDate today, List<Employee> people, List<Separation> exits) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth().isAfter(today) ? today : month.atEndOfMonth();
        List<Separation> inMonth = exits.stream()
                .filter(s -> !s.getLastWorkingDay().isBefore(from) && !s.getLastWorkingDay().isAfter(to))
                .toList();
        return kpis(from, to, people, inMonth);
    }

    /** Why people left in the period, and what they said in the exit survey (AAS-F-043). */
    public ExitInsights exitInsights(CurrentUser user, LocalDate from, LocalDate to, Long departmentId) {
        if (to.isBefore(from)) {
            throw ApiException.badRequest("'to' must not be before 'from'");
        }
        Long dept = scope.departmentFilter(user, departmentId);
        LocalDate today = LocalDate.now();
        LocalDate effectiveTo = to.isAfter(today) ? today : to;
        Set<Long> inScope = employees.findByOrgId(user.orgId()).stream()
                .filter(e -> dept == null || dept.equals(e.getDepartmentId()))
                .map(Employee::getId)
                .collect(Collectors.toSet());
        List<Separation> exits = effectiveTo.isBefore(from) ? List.of()
                : separations.findByOrgIdAndLastWorkingDayBetweenOrderByLastWorkingDayDesc(user.orgId(), from, effectiveTo)
                        .stream().filter(Separation::countsAsAttrition)
                        .filter(s -> inScope.contains(s.getEmployeeId())).toList();

        Map<Long, ExitReason> reasonById = reasons.findByOrgIdOrderByName(user.orgId()).stream()
                .collect(Collectors.toMap(ExitReason::getId, Function.identity()));
        BigDecimal total = BigDecimal.valueOf(exits.size());
        List<ReasonRow> ranked = exits.stream()
                .collect(Collectors.groupingBy(Separation::getReasonId, Collectors.counting()))
                .entrySet().stream()
                .map(en -> {
                    ExitReason r = reasonById.get(en.getKey());
                    return new ReasonRow(r.getName(), r.getCategory(), en.getValue(), AttritionCalculator.rate(en.getValue(), total));
                })
                .sorted(Comparator.comparing(ReasonRow::separations).reversed().thenComparing(ReasonRow::reason))
                .toList();

        Set<Long> exitIds = exits.stream().map(Separation::getId).collect(Collectors.toSet());
        List<SurveyResponse> answers = surveyResponses.findByOrgId(user.orgId()).stream()
                .filter(r -> exitIds.contains(r.getSeparationId()))
                .toList();
        return new ExitInsights(ranked, scores(answers, user.role() == Role.DEPT_MANAGER));
    }

    private static SurveyScores scores(List<SurveyResponse> answers, boolean applyThreshold) {
        if (answers.isEmpty() || (applyThreshold && answers.size() < MIN_SURVEY_RESPONSES)) {
            return new SurveyScores(answers.size(), true, null, null, null, null, null);
        }
        return new SurveyScores(answers.size(), false, average(answers, SurveyResponse::getManager),
                average(answers, SurveyResponse::getPay), average(answers, SurveyResponse::getGrowth),
                average(answers, SurveyResponse::getWorkLife), average(answers, SurveyResponse::getCulture));
    }

    private static BigDecimal average(List<SurveyResponse> answers, ToIntFunction<SurveyResponse> rating) {
        long sum = answers.stream().mapToInt(rating).sum();
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(answers.size()), 2, RoundingMode.HALF_UP);
    }

    private static Kpis kpis(LocalDate from, LocalDate to, List<Employee> people, List<Separation> exits) {
        int opening = (int) people.stream().filter(e -> e.isEmployedOn(from)).count();
        int closing = (int) people.stream().filter(e -> e.isEmployedOn(to)).count();
        BigDecimal avg = AttritionCalculator.averageHeadcount(opening, closing);
        long voluntary = exits.stream().filter(s -> s.getType() == Separation.Type.VOLUNTARY).count();
        long regretted = exits.stream().filter(Separation::isRegretted).count();
        Map<Long, LocalDate> joined = people.stream().collect(Collectors.toMap(Employee::getId, Employee::getJoinDate));
        long early = exits.stream()
                .filter(s -> AttritionCalculator.isEarlyExit(joined.get(s.getEmployeeId()), s.getLastWorkingDay()))
                .count();
        return new Kpis(from, to, opening, closing, avg, exits.size(), voluntary, regretted, early,
                AttritionCalculator.rate(exits.size(), avg), AttritionCalculator.rate(voluntary, avg),
                AttritionCalculator.rate(regretted, avg), AttritionCalculator.rate(early, avg),
                AttritionCalculator.rate(early, BigDecimal.valueOf(exits.size())));
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
