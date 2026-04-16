package com.toir.actualcostrouteoverride;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActualCostReviewRouteOverrideRepository extends JpaRepository<ActualCostReviewRouteOverride, UUID> {
    List<ActualCostReviewRouteOverride> findAllByActualCostIdOrderByCreatedAtDesc(UUID actualCostId);
    Optional<ActualCostReviewRouteOverride> findFirstByActualCostIdAndActiveTrueOrderByCreatedAtDesc(UUID actualCostId);
    List<ActualCostReviewRouteOverride> findAllByActiveTrue();
}
