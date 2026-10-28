package com.aas.config;

import com.aas.auth.AppUser;
import com.aas.auth.AppUserRepository;
import com.aas.auth.Role;
import com.aas.org.Department;
import com.aas.org.DepartmentRepository;
import com.aas.org.OrgService;
import com.aas.org.Organization;
import com.aas.org.OrganizationRepository;
import java.util.LinkedHashMap;
import java.util.Map;
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
    private final PasswordEncoder encoder;

    public DevDataSeeder(OrganizationRepository orgs, OrgService orgService, DepartmentRepository departments,
                         AppUserRepository users, PasswordEncoder encoder) {
        this.orgs = orgs;
        this.orgService = orgService;
        this.departments = departments;
        this.users = users;
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
        log.info("Seeded demo organization 'Acme Technologies' (password for all demo users: {})", DEMO_PASSWORD);
    }
}
