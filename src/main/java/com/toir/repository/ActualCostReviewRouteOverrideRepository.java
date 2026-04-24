package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.ActualCostReviewRouteOverride;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface ActualCostReviewRouteOverrideRepository extends JpaRepository<ActualCostReviewRouteOverride, UUID> {
    List<ActualCostReviewRouteOverride> findAllByActualCostIdOrderByCreatedAtDesc(UUID actualCostId);
    Optional<ActualCostReviewRouteOverride> findFirstByActualCostIdAndActiveTrueOrderByCreatedAtDesc(UUID actualCostId);
    List<ActualCostReviewRouteOverride> findAllByActiveTrue();
}
