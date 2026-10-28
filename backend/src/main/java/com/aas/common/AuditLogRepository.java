package com.aas.common;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByOrgIdAndEntityAndEntityIdOrderByCreatedAt(Long orgId, String entity, Long entityId);
}
