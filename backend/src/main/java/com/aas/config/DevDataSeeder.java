package com.aas.config;

import com.aas.auth.AppUser;
import com.aas.auth.AppUserRepository;
import com.aas.auth.Role;
import com.aas.employee.Employee;
import com.aas.employee.EmployeeRepository;
import com.aas.employee.HeadcountService;
import com.aas.org.Department;
import com.aas.org.DepartmentRepository;
import com.aas.org.OrgService;
import com.aas.org.Organization;
import com.aas.org.OrganizationRepository;
import java.time.LocalDate;
import java.util.LinkedHashMap;
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
    private final PasswordEncoder encoder;

    public DevDataSeeder(OrganizationRepository orgs, OrgService orgService, DepartmentRepository departments,
                         AppUserRepository users, EmployeeRepository employees, HeadcountService headcount,
                         PasswordEncoder encoder) {
        this.orgs = orgs;
        this.orgService = orgService;
        this.departments = departments;
        this.users = users;
        this.employees = employees;
        this.headcount = headcount;
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
        backfillSnapshots(org);
        log.info("Seeded demo organization 'Acme Technologies' (password for all demo users: {})", DEMO_PASSWORD);
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
