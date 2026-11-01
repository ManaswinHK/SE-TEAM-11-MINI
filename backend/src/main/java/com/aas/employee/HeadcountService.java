package com.aas.employee;

import com.aas.org.Department;
import com.aas.org.DepartmentRepository;
import com.aas.org.Organization;
import com.aas.org.OrganizationRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Headcount on any day and monthly snapshots per department (AAS-F-013).
 * Average headcount for a period = (headcount at start + headcount at end) / 2, as defined in the SRS.
 */
@Service
public class HeadcountService {

    private static final Logger log = LoggerFactory.getLogger(HeadcountService.class);

    private final EmployeeRepository employees;
    private final HeadcountSnapshotRepository snapshots;
    private final DepartmentRepository departments;
    private final OrganizationRepository orgs;

    public HeadcountService(EmployeeRepository employees, HeadcountSnapshotRepository snapshots,
                            DepartmentRepository departments, OrganizationRepository orgs) {
        this.employees = employees;
        this.snapshots = snapshots;
        this.departments = departments;
        this.orgs = orgs;
    }

    /** People on the payroll at the end of the given day, optionally for one department. */
    public int headcountOn(Long orgId, Long departmentId, LocalDate day) {
        List<Employee> list = departmentId == null ? employees.findByOrgId(orgId)
                : employees.findByOrgIdAndDepartmentId(orgId, departmentId);
        return (int) list.stream().filter(e -> e.isEmployedOn(day)).count();
    }

    public static BigDecimal average(int opening, int closing) {
        return BigDecimal.valueOf(opening + closing).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    /** Takes (or refreshes) the snapshot of every department for the given date. */
    @Transactional
    public Map<Long, Integer> takeSnapshot(Long orgId, LocalDate date) {
        Map<Long, Integer> counts = new LinkedHashMap<>();
        List<Employee> all = employees.findByOrgId(orgId);
        for (Department d : departments.findByOrgIdOrderByName(orgId)) {
            int count = (int) all.stream().filter(e -> e.getDepartmentId().equals(d.getId()) && e.isEmployedOn(date)).count();
            snapshots.findByOrgIdAndDepartmentIdAndSnapshotDate(orgId, d.getId(), date)
                    .ifPresentOrElse(s -> s.setHeadcount(count),
                            () -> snapshots.save(new HeadcountSnapshot(orgId, d.getId(), date, count)));
            counts.put(d.getId(), count);
        }
        return counts;
    }

    /** Runs shortly after midnight on the 1st of each month for every organization. */
    @Scheduled(cron = "${aas.headcount.snapshot-cron:0 5 0 1 * *}")
    @Transactional
    public void monthlySnapshot() {
        LocalDate today = LocalDate.now();
        for (Organization org : orgs.findAll()) {
            takeSnapshot(org.getId(), today);
        }
        log.info("Monthly headcount snapshot taken for {}", today);
    }

    public List<HeadcountSnapshot> history(Long orgId, LocalDate from, LocalDate to) {
        return snapshots.findByOrgIdAndSnapshotDateBetweenOrderBySnapshotDate(orgId, from, to);
    }
}
