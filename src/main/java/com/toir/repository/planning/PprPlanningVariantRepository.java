package com.toir.repository.planning;

import com.toir.entity.planning.PprPlanningVariant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PprPlanningVariantRepository extends JpaRepository<PprPlanningVariant, UUID> {

    List<PprPlanningVariant> findAllBySessionIdAndIsDeletedFalseOrderByCreatedAtAsc(UUID sessionId);

    Optional<PprPlanningVariant> findByIdAndSessionIdAndIsDeletedFalse(
            UUID id,
            UUID sessionId);
    boolean existsBySessionIdAndNormalizedNameAndIsDeletedFalse(
            UUID sessionId, String normalizedName);

    boolean existsBySessionIdAndNormalizedNameAndIdNotAndIsDeletedFalse(
            UUID sessionId, String normalizedName, UUID id);
}
