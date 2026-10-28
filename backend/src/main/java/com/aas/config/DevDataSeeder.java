package com.aas.config;

import com.aas.auth.AppUser;
import com.aas.auth.AppUserRepository;
import com.aas.auth.Role;
import com.aas.org.Organization;
import com.aas.org.OrganizationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates a demo organization with one user per role the first time the dev database starts. */
@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {

    public static final String DEMO_PASSWORD = "Demo@1234";
    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final OrganizationRepository orgs;
    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    public DevDataSeeder(OrganizationRepository orgs, AppUserRepository users, PasswordEncoder encoder) {
        this.orgs = orgs;
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (orgs.count() > 0) {
            return;
        }
        Organization org = orgs.save(new Organization("Acme Technologies", "IT Services"));
        String hash = encoder.encode(DEMO_PASSWORD);
        users.save(new AppUser(org.getId(), "admin@acme.test", "Asha Admin", hash, Role.HR_ADMIN));
        users.save(new AppUser(org.getId(), "hr@acme.test", "Hari HR Manager", hash, Role.HR_MANAGER));
        users.save(new AppUser(org.getId(), "manager@acme.test", "Meera Manager", hash, Role.DEPT_MANAGER));
        users.save(new AppUser(org.getId(), "ceo@acme.test", "Lakshmi Leadership", hash, Role.LEADERSHIP));
        log.info("Seeded demo organization 'Acme Technologies' (password for all demo users: {})", DEMO_PASSWORD);
    }
}
