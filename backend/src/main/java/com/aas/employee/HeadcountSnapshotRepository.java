package com.aas.employee;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HeadcountSnapshotRepository extends JpaRepository<HeadcountSnapshot, Long> {

    Optional<HeadcountSnapshot> findByOrgIdAndDepartmentIdAndSnapshotDate(Long orgId, Long departmentId, LocalDate date);

    List<HeadcountSnapshot> findByOrgIdAndSnapshotDateBetweenOrderBySnapshotDate(Long orgId, LocalDate from, LocalDate to);
}
