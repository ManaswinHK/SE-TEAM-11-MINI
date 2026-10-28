package com.aas.org;

import com.aas.auth.AppUser;
import com.aas.auth.AppUserRepository;
import com.aas.auth.Role;
import com.aas.common.ApiException;
import com.aas.org.ExitReason.Category;
import com.aas.org.OrgDtos.DepartmentRequest;
import com.aas.org.OrgDtos.ExitReasonRequest;
import com.aas.org.OrgDtos.RegisterOrgRequest;
import com.aas.org.OrgDtos.UpdateOrgRequest;
import java.util.List;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrgService {

    /** Starting list of exit reasons for a new organization; HR Admins can rename or add more. */
    static final Map<String, Category> DEFAULT_REASONS = Map.ofEntries(
            Map.entry("Compensation", Category.VOLUNTARY),
            Map.entry("Career growth", Category.VOLUNTARY),
            Map.entry("Manager / leadership", Category.VOLUNTARY),
            Map.entry("Work-life balance", Category.VOLUNTARY),
            Map.entry("Relocation", Category.VOLUNTARY),
            Map.entry("Higher studies", Category.VOLUNTARY),
            Map.entry("Health / personal", Category.VOLUNTARY),
            Map.entry("Performance", Category.INVOLUNTARY),
            Map.entry("Layoff / restructuring", Category.INVOLUNTARY),
            Map.entry("Misconduct", Category.INVOLUNTARY),
            Map.entry("Retirement", Category.OTHER),
            Map.entry("End of contract", Category.OTHER));

    private final OrganizationRepository orgs;
    private final DepartmentRepository departments;
    private final ExitReasonRepository reasons;
    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    public OrgService(OrganizationRepository orgs, DepartmentRepository departments, ExitReasonRepository reasons,
                      AppUserRepository users, PasswordEncoder encoder) {
        this.orgs = orgs;
        this.departments = departments;
        this.reasons = reasons;
        this.users = users;
        this.encoder = encoder;
    }

    @Transactional
    public Organization createOrganization(String name, String industry) {
        Organization org = orgs.save(new Organization(name.trim(), industry));
        DEFAULT_REASONS.forEach((reason, category) -> reasons.save(new ExitReason(org.getId(), reason, category)));
        return org;
    }

    /** Self-service sign-up so any organization can start using AAS (AAS-F-003). */
    @Transactional
    public Organization register(RegisterOrgRequest req) {
        if (users.existsByEmailIgnoreCase(req.adminEmail())) {
            throw ApiException.conflict("An account with this email already exists");
        }
        Organization org = createOrganization(req.organizationName(), req.industry());
        users.save(new AppUser(org.getId(), req.adminEmail().trim(), req.adminName().trim(),
                encoder.encode(req.password()), Role.HR_ADMIN));
        return org;
    }

    public Organization get(Long orgId) {
        return orgs.findById(orgId).orElseThrow(() -> ApiException.notFound("Organization"));
    }

    @Transactional
    public Organization update(Long orgId, UpdateOrgRequest req) {
        Organization org = get(orgId);
        org.setName(req.name().trim());
        org.setIndustry(req.industry());
        org.setRiskThreshold(req.riskThreshold());
        return org;
    }

    public List<Department> departments(Long orgId) {
        return departments.findByOrgIdOrderByName(orgId);
    }

    public Department department(Long orgId, Long id) {
        return departments.findByIdAndOrgId(id, orgId).orElseThrow(() -> ApiException.notFound("Department"));
    }

    @Transactional
    public Department createDepartment(Long orgId, DepartmentRequest req) {
        if (departments.existsByOrgIdAndNameIgnoreCase(orgId, req.name().trim())) {
            throw ApiException.conflict("A department with this name already exists");
        }
        return departments.save(new Department(orgId, req.name().trim(), req.location()));
    }

    @Transactional
    public Department updateDepartment(Long orgId, Long id, DepartmentRequest req) {
        Department d = department(orgId, id);
        String name = req.name().trim();
        if (!d.getName().equalsIgnoreCase(name) && departments.existsByOrgIdAndNameIgnoreCase(orgId, name)) {
            throw ApiException.conflict("A department with this name already exists");
        }
        d.setName(name);
        d.setLocation(req.location());
        if (req.active() != null) {
            d.setActive(req.active());
        }
        return d;
    }

    public List<ExitReason> exitReasons(Long orgId) {
        return reasons.findByOrgIdOrderByName(orgId);
    }

    @Transactional
    public ExitReason createExitReason(Long orgId, ExitReasonRequest req) {
        if (reasons.existsByOrgIdAndNameIgnoreCase(orgId, req.name().trim())) {
            throw ApiException.conflict("This exit reason already exists");
        }
        return reasons.save(new ExitReason(orgId, req.name().trim(), req.category()));
    }

    @Transactional
    public ExitReason updateExitReason(Long orgId, Long id, ExitReasonRequest req) {
        ExitReason r = reasons.findByIdAndOrgId(id, orgId).orElseThrow(() -> ApiException.notFound("Exit reason"));
        String name = req.name().trim();
        if (!r.getName().equalsIgnoreCase(name) && reasons.existsByOrgIdAndNameIgnoreCase(orgId, name)) {
            throw ApiException.conflict("This exit reason already exists");
        }
        r.setName(name);
        r.setCategory(req.category());
        if (req.active() != null) {
            r.setActive(req.active());
        }
        return r;
    }
}
