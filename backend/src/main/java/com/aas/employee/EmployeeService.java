package com.aas.employee;

import com.aas.auth.AccessScope;
import com.aas.auth.CurrentUser;
import com.aas.common.ApiException;
import com.aas.employee.EmployeeDtos.EmployeeRequest;
import com.aas.employee.EmployeeDtos.EmployeeView;
import com.aas.employee.EmployeeDtos.PageView;
import com.aas.org.Department;
import com.aas.org.DepartmentRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeService {

    private final EmployeeRepository employees;
    private final DepartmentRepository departments;
    private final AccessScope scope;

    public EmployeeService(EmployeeRepository employees, DepartmentRepository departments, AccessScope scope) {
        this.employees = employees;
        this.departments = departments;
        this.scope = scope;
    }

    public PageView<EmployeeView> search(CurrentUser user, String query, Long departmentId, EmployeeStatus status, int page, int size) {
        Long dept = scope.departmentFilter(user, departmentId);
        Specification<Employee> spec = (root, q, cb) -> {
            List<jakarta.persistence.criteria.Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("orgId"), user.orgId()));
            if (dept != null) p.add(cb.equal(root.get("departmentId"), dept));
            if (status != null) p.add(cb.equal(root.get("status"), status));
            if (query != null && !query.isBlank()) {
                String like = "%" + query.trim().toLowerCase() + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("fullName")), like), cb.like(cb.lower(root.get("employeeCode")), like)));
            }
            return cb.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        Page<Employee> result = employees.findAll(spec, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200),
                Sort.by("fullName")));
        Map<Long, String> names = departmentNames(user.orgId());
        return new PageView<>(result.map(e -> EmployeeView.of(e, names.get(e.getDepartmentId()))).getContent(),
                result.getTotalElements(), result.getNumber(), result.getSize(), result.getTotalPages());
    }

    public EmployeeView get(CurrentUser user, Long id) {
        Employee e = find(user, id);
        scope.checkDepartment(user, e.getDepartmentId());
        return EmployeeView.of(e, departmentNames(user.orgId()).get(e.getDepartmentId()));
    }

    @Transactional
    public EmployeeView create(CurrentUser user, EmployeeRequest req) {
        validate(user.orgId(), req);
        if (employees.existsByOrgIdAndEmployeeCodeIgnoreCase(user.orgId(), req.employeeCode())) {
            throw ApiException.conflict("Employee ID " + req.employeeCode() + " already exists");
        }
        Employee e = new Employee(user.orgId(), req.employeeCode().trim(), req.fullName().trim(), req.departmentId(), req.joinDate());
        apply(e, req);
        return EmployeeView.of(employees.save(e), departmentNames(user.orgId()).get(e.getDepartmentId()));
    }

    @Transactional
    public EmployeeView update(CurrentUser user, Long id, EmployeeRequest req) {
        Employee e = find(user, id);
        validate(user.orgId(), req);
        if (!e.getEmployeeCode().equalsIgnoreCase(req.employeeCode())
                && employees.existsByOrgIdAndEmployeeCodeIgnoreCase(user.orgId(), req.employeeCode())) {
            throw ApiException.conflict("Employee ID " + req.employeeCode() + " already exists");
        }
        e.setEmployeeCode(req.employeeCode().trim());
        e.setFullName(req.fullName().trim());
        e.setJoinDate(req.joinDate());
        e.setDepartmentId(req.departmentId());
        apply(e, req);
        return EmployeeView.of(e, departmentNames(user.orgId()).get(e.getDepartmentId()));
    }

    /** Removes a record entered by mistake. Real exits must be recorded as separations instead. */
    @Transactional
    public EmployeeView deactivate(CurrentUser user, Long id) {
        Employee e = find(user, id);
        e.setStatus(EmployeeStatus.INACTIVE);
        return EmployeeView.of(e, departmentNames(user.orgId()).get(e.getDepartmentId()));
    }

    Employee find(CurrentUser user, Long id) {
        return employees.findByIdAndOrgId(id, user.orgId()).orElseThrow(() -> ApiException.notFound("Employee"));
    }

    Map<Long, String> departmentNames(Long orgId) {
        return departments.findByOrgIdOrderByName(orgId).stream().collect(Collectors.toMap(Department::getId, Department::getName));
    }

    Map<Long, Department> departmentsById(Long orgId) {
        return departments.findByOrgIdOrderByName(orgId).stream().collect(Collectors.toMap(Department::getId, Function.identity()));
    }

    private void validate(Long orgId, EmployeeRequest req) {
        if (departments.findByIdAndOrgId(req.departmentId(), orgId).isEmpty()) {
            throw ApiException.badRequest("Unknown department");
        }
        if (req.lastPromotionDate() != null && req.lastPromotionDate().isBefore(req.joinDate())) {
            throw ApiException.badRequest("Last promotion date cannot be before the join date");
        }
    }

    private static void apply(Employee e, EmployeeRequest req) {
        e.setEmail(blankToNull(req.email()));
        e.setDesignation(blankToNull(req.designation()));
        e.setManagerName(blankToNull(req.managerName()));
        e.setLocation(blankToNull(req.location()));
        e.setGrade(blankToNull(req.grade()));
        e.setGender(blankToNull(req.gender()));
        e.setPerformanceRating(req.performanceRating());
        e.setLastPromotionDate(req.lastPromotionDate());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
