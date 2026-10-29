package com.aas.org;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    List<Department> findByOrgIdOrderByName(Long orgId);

    Optional<Department> findByIdAndOrgId(Long id, Long orgId);

    Optional<Department> findByOrgIdAndNameIgnoreCase(Long orgId, String name);

    boolean existsByOrgIdAndNameIgnoreCase(Long orgId, String name);
}
