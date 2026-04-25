package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.ActualCostReviewRouteOverride;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface ActualCostReviewRouteOverrideRepository extends JpaRepository<ActualCostReviewRouteOverride, UUID> {
    java.util.Optional<ActualCostReviewRouteOverride> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<ActualCostReviewRouteOverride> findAllByIsDeletedFalse();

    java.util.List<ActualCostReviewRouteOverride> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM actual_cost_review_route_overrides WHERE actual_cost_id = :actualCostId AND is_deleted = false ORDER BY created_at DESC", nativeQuery = true)
    List<ActualCostReviewRouteOverride> findAllByActualCostIdAndIsDeletedFalseOrderByCreatedAtDesc(@Param("actualCostId") UUID actualCostId);

    @Query(value = "SELECT * FROM actual_cost_review_route_overrides WHERE actual_cost_id = :actualCostId AND is_active = true AND is_deleted = false ORDER BY created_at DESC LIMIT 1", nativeQuery = true)
    Optional<ActualCostReviewRouteOverride> findFirstByActualCostIdAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(@Param("actualCostId") UUID actualCostId);

    @Query(value = "SELECT * FROM actual_cost_review_route_overrides WHERE is_active = true AND is_deleted = false", nativeQuery = true)
    List<ActualCostReviewRouteOverride> findAllByActiveTrueAndIsDeletedFalse();
}
