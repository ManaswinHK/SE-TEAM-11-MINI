package com.aas.risk;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetentionActionRepository extends JpaRepository<RetentionAction, Long> {

    List<RetentionAction> findByOrgIdOrderByDueDate(Long orgId);

    Optional<RetentionAction> findByIdAndOrgId(Long id, Long orgId);
}
