package com.aas.separation;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClearanceItemRepository extends JpaRepository<ClearanceItem, Long> {

    List<ClearanceItem> findBySeparationIdOrderBySortOrder(Long separationId);

    Optional<ClearanceItem> findByIdAndSeparationId(Long id, Long separationId);
}
