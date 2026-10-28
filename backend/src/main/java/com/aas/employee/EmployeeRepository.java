package com.aas.employee;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    Optional<Employee> findByIdAndOrgId(Long id, Long orgId);

    boolean existsByOrgIdAndEmployeeCodeIgnoreCase(Long orgId, String employeeCode);

    List<Employee> findByOrgId(Long orgId);

    List<Employee> findByOrgIdAndDepartmentId(Long orgId, Long departmentId);
}
