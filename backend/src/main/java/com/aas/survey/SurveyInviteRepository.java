package com.aas.survey;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SurveyInviteRepository extends JpaRepository<SurveyInvite, Long> {

    List<SurveyInvite> findByOrgId(Long orgId);

    Optional<SurveyInvite> findBySeparationId(Long separationId);

    Optional<SurveyInvite> findByToken(String token);

    List<SurveyInvite> findBySentAtIsNullAndCancelledAtIsNullAndExpiresAtAfter(LocalDateTime now);

    List<SurveyInvite> findBySentAtIsNotNullAndSubmittedAtIsNullAndCancelledAtIsNullAndExpiresAtAfter(LocalDateTime now);

    /** Returns 0 if the survey was already submitted, so two simultaneous submissions cannot both succeed. */
    @Modifying
    @Query("update SurveyInvite i set i.submittedAt = :now where i.id = :id and i.submittedAt is null")
    int markSubmitted(@Param("id") Long id, @Param("now") LocalDateTime now);
}
