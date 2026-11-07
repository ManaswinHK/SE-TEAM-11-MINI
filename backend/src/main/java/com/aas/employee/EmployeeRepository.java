package com.aas.employee;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    Optional<Employee> findByIdAndOrgId(Long id, Long orgId);

    boolean existsByOrgIdAndEmployeeCodeIgnoreCase(Long orgId, String employeeCode);

    List<Employee> findByOrgId(Long orgId);

    /** Just the fields analytics needs, without loading or decrypting whole employees. */
    @Query("select new com.aas.employee.Person(e.id, e.departmentId, e.joinDate, e.lastWorkingDay, e.status, e.location) "
            + "from Employee e where e.orgId = :orgId")
    List<Person> findPeople(@Param("orgId") Long orgId);

    @Query("select new com.aas.employee.EmployeeRef(e.id, e.employeeCode, e.fullName, e.departmentId, e.joinDate) "
            + "from Employee e where e.orgId = :orgId and e.id in :ids")
    List<EmployeeRef> findRefs(@Param("orgId") Long orgId, @Param("ids") List<Long> ids);

    List<Employee> findByOrgIdAndDepartmentId(Long orgId, Long departmentId);
}
