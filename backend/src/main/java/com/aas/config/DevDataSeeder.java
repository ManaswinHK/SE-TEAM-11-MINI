package com.aas.config;

import com.aas.auth.AppUser;
import com.aas.auth.AppUserRepository;
import com.aas.auth.Role;
import com.aas.employee.Employee;
import com.aas.employee.EmployeeRepository;
import com.aas.employee.EmployeeStatus;
import com.aas.employee.HeadcountService;
import com.aas.org.Department;
import com.aas.org.DepartmentRepository;
import com.aas.org.ExitReason;
import com.aas.org.ExitReasonRepository;
import com.aas.org.OrgService;
import com.aas.org.Organization;
import com.aas.org.OrganizationRepository;
import com.aas.separation.Separation;
import com.aas.separation.SeparationRepository;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates a demo organization with departments and one user per role the first time the dev database starts. */
@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {

    public static final String DEMO_PASSWORD = "Demo@1234";
    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final OrganizationRepository orgs;
    private final OrgService orgService;
    private final DepartmentRepository departments;
    private final AppUserRepository users;
    private final EmployeeRepository employees;
    private final HeadcountService headcount;
    private final ExitReasonRepository reasons;
    private final SeparationRepository separations;
    private final PasswordEncoder encoder;

    public DevDataSeeder(OrganizationRepository orgs, OrgService orgService, DepartmentRepository departments,
                         AppUserRepository users, EmployeeRepository employees, HeadcountService headcount,
                         ExitReasonRepository reasons, SeparationRepository separations, PasswordEncoder encoder) {
        this.orgs = orgs;
        this.orgService = orgService;
        this.departments = departments;
        this.users = users;
        this.employees = employees;
        this.headcount = headcount;
        this.reasons = reasons;
        this.separations = separations;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (orgs.count() > 0) {
            return;
        }
        Organization org = orgService.createOrganization("Acme Technologies", "IT Services");
        Map<String, Department> depts = new LinkedHashMap<>();
        for (String[] d : new String[][] {
                {"Engineering", "Bengaluru"}, {"Sales", "Mumbai"}, {"Customer Support", "Hyderabad"},
                {"Finance", "Bengaluru"}, {"Human Resources", "Bengaluru"}, {"Marketing", "Pune"}}) {
            depts.put(d[0], departments.save(new Department(org.getId(), d[0], d[1])));
        }

        String hash = encoder.encode(DEMO_PASSWORD);
        users.save(new AppUser(org.getId(), "admin@acme.test", "Asha Admin", hash, Role.HR_ADMIN));
        users.save(new AppUser(org.getId(), "hr@acme.test", "Hari HR Manager", hash, Role.HR_MANAGER));
        AppUser manager = new AppUser(org.getId(), "manager@acme.test", "Meera Manager (Sales)", hash, Role.DEPT_MANAGER);
        manager.setDepartmentId(depts.get("Sales").getId());
        users.save(manager);
        users.save(new AppUser(org.getId(), "ceo@acme.test", "Lakshmi Leadership", hash, Role.LEADERSHIP));
        seedEmployees(org, depts);
        seedSeparations(org, depts);
        backfillSnapshots(org);
        log.info("Seeded demo organization 'Acme Technologies' (password for all demo users: {})", DEMO_PASSWORD);
    }

    /**
     * About 55 exits since January 2025 (higher in Sales and Customer Support), plus a few people still serving
     * notice, with a realistic mix of exit reasons.
     */
    private void seedSeparations(Organization org, Map<String, Department> depts) {
        Random rnd = new Random(7);
        Map<String, ExitReason> byName = new LinkedHashMap<>();
        reasons.findByOrgIdOrderByName(org.getId()).forEach(r -> byName.put(r.getName(), r));
        String[] weighted = {"Compensation", "Compensation", "Compensation", "Compensation", "Compensation", "Compensation",
                "Career growth", "Career growth", "Career growth", "Career growth", "Career growth",
                "Manager / leadership", "Manager / leadership", "Work-life balance", "Work-life balance",
                "Relocation", "Higher studies", "Performance", "Layoff / restructuring", "End of contract", "Retirement"};
        Map<Long, Double> deptWeight = Map.of(depts.get("Sales").getId(), 1.0, depts.get("Customer Support").getId(), 0.9);
        List<Employee> pool = employees.findByOrgId(org.getId());
        LocalDate first = LocalDate.of(2025, 1, 1);
        int span = (int) (LocalDate.now().minusDays(3).toEpochDay() - first.toEpochDay());
        int completed = 0;
        int notice = 0;
        while (completed < 55 || notice < 5) {
            Employee e = pool.get(rnd.nextInt(pool.size()));
            if (e.getStatus() != EmployeeStatus.ACTIVE || rnd.nextDouble() > deptWeight.getOrDefault(e.getDepartmentId(), 0.45)) {
                continue;
            }
            boolean serving = completed >= 55;
            // People still serving notice resigned in the last three weeks and leave within the next one to six weeks.
            LocalDate resigned = serving ? LocalDate.now().minusDays(1 + rnd.nextInt(20)) : null;
            LocalDate lastDay = serving ? resigned.plusDays(30 + rnd.nextInt(14)) : first.plusDays(rnd.nextInt(span));
            if (!serving) {
                resigned = lastDay.minusDays(30 + rnd.nextInt(30));
            }
            if (resigned.isBefore(e.getJoinDate().plusMonths(2))) {
                continue;
            }
            ExitReason reason = byName.get(weighted[rnd.nextInt(weighted.length)]);
            Separation.Type type = switch (reason.getCategory()) {
                case VOLUNTARY -> Separation.Type.VOLUNTARY;
                case INVOLUNTARY -> Separation.Type.INVOLUNTARY;
                case OTHER -> reason.getName().equals("Retirement") ? Separation.Type.RETIREMENT : Separation.Type.END_OF_CONTRACT;
            };
            boolean regretted = type == Separation.Type.VOLUNTARY && e.getPerformanceRating() != null && e.getPerformanceRating() >= 4;
            Separation sep = separations.save(new Separation(org.getId(), e.getId(), resigned, lastDay, type, reason.getId(),
                    regretted, null, null));
            e.setLastWorkingDay(lastDay);
            e.setStatus(sep.getStatus() == Separation.Status.COMPLETED ? EmployeeStatus.SEPARATED : EmployeeStatus.NOTICE_PERIOD);
            if (serving) notice++; else completed++;
        }
    }

    /** Monthly snapshots since January 2025 so headcount history is available immediately. */
    private void backfillSnapshots(Organization org) {
        for (LocalDate d = LocalDate.of(2025, 1, 1); !d.isAfter(LocalDate.now()); d = d.plusMonths(1)) {
            headcount.takeSnapshot(org.getId(), d);
        }
    }

    private static final String[] FIRST = {"Aarav", "Aditi", "Akash", "Ananya", "Arjun", "Bhavna", "Chetan", "Deepa",
            "Divya", "Farhan", "Gaurav", "Isha", "Karan", "Kavya", "Lokesh", "Meghna", "Nikhil", "Neha", "Pooja",
            "Pranav", "Rahul", "Riya", "Rohan", "Sanjana", "Shreya", "Siddharth", "Sneha", "Tanvi", "Varun", "Zoya"};
    private static final String[] LAST = {"Sharma", "Iyer", "Reddy", "Nair", "Patel", "Rao", "Gupta", "Menon",
            "Kulkarni", "Shetty", "Das", "Joshi", "Singh", "Hegde", "Bose", "Pillai"};

    /** About 240 people with join dates spread over the last seven years, so dashboards have realistic data. */
    private void seedEmployees(Organization org, Map<String, Department> depts) {
        Random rnd = new Random(42);
        Map<String, Integer> sizes = Map.of("Engineering", 90, "Sales", 50, "Customer Support", 45,
                "Finance", 20, "Human Resources", 12, "Marketing", 23);
        LocalDate start = LocalDate.of(2019, 1, 1);
        int span = (int) (LocalDate.of(2026, 9, 30).toEpochDay() - start.toEpochDay());
        int n = 1;
        for (Map.Entry<String, Department> d : depts.entrySet()) {
            for (int i = 0; i < sizes.get(d.getKey()); i++) {
                String name = FIRST[rnd.nextInt(FIRST.length)] + " " + LAST[rnd.nextInt(LAST.length)];
                LocalDate joined = start.plusDays(rnd.nextInt(span));
                Employee e = new Employee(org.getId(), String.format("ACM%04d", n++), name, d.getValue().getId(), joined);
                e.setEmail(name.toLowerCase().replace(' ', '.') + n + "@acme.test");
                e.setDesignation(d.getKey().equals("Engineering") ? "Software Engineer" : d.getKey() + " Executive");
                e.setLocation(d.getValue().getLocation());
                e.setGrade("L" + (1 + rnd.nextInt(5)));
                e.setGender(rnd.nextBoolean() ? "Female" : "Male");
                e.setPerformanceRating(1 + Math.min(4, (int) Math.round(Math.abs(rnd.nextGaussian() * 1.1 + 2.4))));
                if (rnd.nextInt(3) > 0 && joined.isBefore(LocalDate.of(2025, 6, 1))) {
                    e.setLastPromotionDate(joined.plusMonths(12 + rnd.nextInt(18)));
                    if (e.getLastPromotionDate().isAfter(LocalDate.now())) e.setLastPromotionDate(null);
                }
                employees.save(e);
            }
        }
    }
}
