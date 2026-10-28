package com.aas.separation;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeparationRepository extends JpaRepository<Separation, Long> {

    Optional<Separation> findByIdAndOrgId(Long id, Long orgId);

    List<Separation> findByOrgIdAndLastWorkingDayBetweenOrderByLastWorkingDayDesc(Long orgId, LocalDate from, LocalDate to);

    List<Separation> findByStatusAndLastWorkingDayBefore(Separation.Status status, LocalDate day);
}
