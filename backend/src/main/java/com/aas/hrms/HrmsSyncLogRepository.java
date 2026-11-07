package com.aas.hrms;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HrmsSyncLogRepository extends JpaRepository<HrmsSyncLog, Long> {

    List<HrmsSyncLog> findTop20ByOrgIdOrderByStartedAtDesc(Long orgId);
}
