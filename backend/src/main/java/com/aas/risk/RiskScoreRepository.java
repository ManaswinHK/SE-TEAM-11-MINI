package com.aas.risk;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RiskScoreRepository extends JpaRepository<RiskScore, Long> {

    @Query("select max(r.scoredOn) from RiskScore r where r.orgId = :orgId")
    Optional<LocalDate> latestRun(Long orgId);

    List<RiskScore> findByOrgIdAndScoredOn(Long orgId, LocalDate scoredOn);

    @Modifying
    @Query("delete from RiskScore r where r.orgId = :orgId and r.scoredOn = :scoredOn")
    void deleteRun(Long orgId, LocalDate scoredOn);
}
