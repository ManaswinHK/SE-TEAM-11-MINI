package com.aas.org;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExitReasonRepository extends JpaRepository<ExitReason, Long> {

    List<ExitReason> findByOrgIdOrderByName(Long orgId);

    Optional<ExitReason> findByIdAndOrgId(Long id, Long orgId);

    boolean existsByOrgIdAndNameIgnoreCase(Long orgId, String name);
}
