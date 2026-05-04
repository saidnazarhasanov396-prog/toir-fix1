package com.toir.repository.actualCost;

import com.toir.entity.projects.ActualCostReviewRouteOverride;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface ActualCostReviewRouteOverrideRepository extends JpaRepository<ActualCostReviewRouteOverride, UUID> {
    @Query(value = "SELECT * FROM actual_cost_review_route_overrides WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<ActualCostReviewRouteOverride> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM actual_cost_review_route_overrides WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<ActualCostReviewRouteOverride> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM actual_cost_review_route_overrides WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<ActualCostReviewRouteOverride> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM actual_cost_review_route_overrides WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM actual_cost_review_route_overrides WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM actual_cost_review_route_overrides WHERE actual_cost_id = :actualCostId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<ActualCostReviewRouteOverride> findAllByActualCostIdAndIsDeletedFalseOrderByCreatedAtDesc(@Param("actualCostId") UUID actualCostId);

    @Query(value = "SELECT * FROM actual_cost_review_route_overrides WHERE actual_cost_id = :actualCostId AND is_active = true AND is_deleted = false ORDER BY updated_at DESC LIMIT 1", nativeQuery = true)
    Optional<ActualCostReviewRouteOverride> findFirstByActualCostIdAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(@Param("actualCostId") UUID actualCostId);

    @Query(value = "SELECT * FROM actual_cost_review_route_overrides WHERE is_active = true AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<ActualCostReviewRouteOverride> findAllByActiveTrueAndIsDeletedFalse();
}
