package com.aas.survey;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SurveyInviteRepository extends JpaRepository<SurveyInvite, Long> {

    List<SurveyInvite> findByOrgId(Long orgId);

    Optional<SurveyInvite> findBySeparationId(Long separationId);

    List<SurveyInvite> findBySentAtIsNullAndCancelledAtIsNullAndExpiresAtAfter(LocalDateTime now);
}
