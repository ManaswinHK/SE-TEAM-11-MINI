package com.aas.config;

import com.aas.auth.AppUser;
import com.aas.auth.AppUserRepository;
import com.aas.auth.Role;
import com.aas.common.EncryptedStringConverter;
import com.aas.org.Department;
import com.aas.org.DepartmentRepository;
import com.aas.org.ExitReason;
import com.aas.org.ExitReasonRepository;
import com.aas.org.OrgService;
import com.aas.org.Organization;
import com.aas.org.OrganizationRepository;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Performance-test data (AAS-NF-001, TC-PERF-01): one organization with 50,000 employee records and five years of
 * separations, written with batched JDBC so the database is ready in about a minute.
 */
@Component
@Profile("perf")
public class PerfDataSeeder implements CommandLineRunner {

    public static final int EMPLOYEES = 50_000;
    private static final Logger log = LoggerFactory.getLogger(PerfDataSeeder.class);
    private static final String[] DEPARTMENTS = {"Engineering", "Sales", "Customer Support", "Finance", "Human Resources",
            "Marketing", "Operations", "Product", "Legal", "Procurement"};
    private static final String[] CITIES = {"Bengaluru", "Mumbai", "Hyderabad", "Pune", "Chennai"};

    private final OrganizationRepository orgs;
    private final OrgService orgService;
    private final DepartmentRepository departments;
    private final ExitReasonRepository reasons;
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final EncryptedStringConverter crypto;
    private final JdbcTemplate jdbc;

    public PerfDataSeeder(OrganizationRepository orgs, OrgService orgService, DepartmentRepository departments,
                          ExitReasonRepository reasons, AppUserRepository users, PasswordEncoder encoder,
                          EncryptedStringConverter crypto, JdbcTemplate jdbc) {
        this.orgs = orgs;
        this.orgService = orgService;
        this.departments = departments;
        this.reasons = reasons;
        this.users = users;
        this.encoder = encoder;
        this.crypto = crypto;
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        if (orgs.count() > 0) {
            return;
        }
        long started = System.currentTimeMillis();
        Organization org = orgService.createOrganization("Perf Industries", "Manufacturing");
        List<Department> depts = new ArrayList<>();
        for (int i = 0; i < DEPARTMENTS.length; i++) {
            depts.add(departments.save(new Department(org.getId(), DEPARTMENTS[i], CITIES[i % CITIES.length])));
        }
        String hash = encoder.encode(DevDataSeeder.DEMO_PASSWORD);
        users.save(new AppUser(org.getId(), "perf-hr@perf.test", "Perf HR", hash, Role.HR_MANAGER));
        users.save(new AppUser(org.getId(), "perf-ceo@perf.test", "Perf Leadership", hash, Role.LEADERSHIP));

        Random rnd = new Random(2026);
        LocalDate today = LocalDate.now();
        LocalDate historyStart = today.minusYears(5);
        LocalDateTime now = LocalDateTime.now();
        List<Object[]> rows = new ArrayList<>();
        for (int n = 1; n <= EMPLOYEES; n++) {
            Department d = depts.get(rnd.nextInt(depts.size()));
            LocalDate joined = today.minusDays(30 + rnd.nextInt(10 * 365));
            rows.add(new Object[] {org.getId(), String.format("P%06d", n), "Employee " + n,
                    crypto.convertToDatabaseColumn("employee" + n + "@perf.test"), d.getId(), "Associate", d.getLocation(),
                    "L" + (1 + rnd.nextInt(5)), crypto.convertToDatabaseColumn(rnd.nextBoolean() ? "Female" : "Male"),
                    Date.valueOf(joined), 1 + rnd.nextInt(5), "ACTIVE", Timestamp.valueOf(now), Timestamp.valueOf(now)});
        }
        jdbc.batchUpdate("insert into employee (org_id, employee_code, full_name, email, department_id, designation, location, "
                + "grade, gender, join_date, performance_rating, status, created_at, updated_at) "
                + "values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)", rows);

        // About 12% of people leave each year: pick leavers among those who joined at least 2 months before leaving.
        List<ExitReason> reasonList = reasons.findByOrgIdOrderByName(org.getId());
        List<Object[]> people = jdbc.query("select id, join_date from employee where org_id = ?",
                (rs, i) -> new Object[] {rs.getLong(1), rs.getDate(2).toLocalDate()}, org.getId());
        List<Object[]> exits = new ArrayList<>();
        List<Object[]> statuses = new ArrayList<>();
        for (Object[] p : people) {
            LocalDate joined = (LocalDate) p[1];
            if (rnd.nextDouble() > 0.30) {
                continue;
            }
            LocalDate earliest = joined.plusMonths(2).isAfter(historyStart) ? joined.plusMonths(2) : historyStart;
            long span = today.minusDays(1).toEpochDay() - earliest.toEpochDay();
            if (span <= 0) {
                continue;
            }
            LocalDate lastDay = earliest.plusDays((long) (rnd.nextDouble() * span));
            ExitReason reason = reasonList.get(rnd.nextInt(reasonList.size()));
            exits.add(new Object[] {org.getId(), p[0], Date.valueOf(lastDay.minusDays(30)), Date.valueOf(lastDay), "VOLUNTARY",
                    reason.getId(), rnd.nextInt(4) == 0, "COMPLETED", Timestamp.valueOf(now)});
            statuses.add(new Object[] {Date.valueOf(lastDay), p[0]});
        }
        jdbc.batchUpdate("insert into separation (org_id, employee_id, resignation_date, last_working_day, type, reason_id, "
                + "regretted, status, created_at) values (?,?,?,?,?,?,?,?,?)", exits);
        jdbc.batchUpdate("update employee set status = 'SEPARATED', last_working_day = ? where id = ?", statuses);
        log.info("Seeded performance data: {} employees, {} separations in {} s", EMPLOYEES, exits.size(),
                (System.currentTimeMillis() - started) / 1000);
    }
}
