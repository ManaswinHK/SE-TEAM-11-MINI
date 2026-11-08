package com.aas.alert;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttritionAlertRepository extends JpaRepository<AttritionAlert, Long> {

    Optional<AttritionAlert> findFirstByOrgIdAndDepartmentIdAndResolvedAtIsNull(Long orgId, Long departmentId);

    List<AttritionAlert> findTop50ByOrgIdOrderByCreatedAtDesc(Long orgId);
}
