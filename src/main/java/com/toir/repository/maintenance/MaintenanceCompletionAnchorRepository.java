package com.toir.repository.maintenance;

import com.toir.entity.maintenance.MaintenanceCompletionAnchor;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MaintenanceCompletionAnchorRepository extends JpaRepository<MaintenanceCompletionAnchor, UUID> {

    Optional<MaintenanceCompletionAnchor> findByWorkOrderIdAndIsDeletedFalse(UUID workOrderId);

    List<MaintenanceCompletionAnchor> findAllByRepairRequestIdAndIsDeletedFalse(UUID repairRequestId);

    Optional<MaintenanceCompletionAnchor> findByMaintenanceDueEventIdAndIsDeletedFalse(UUID maintenanceDueEventId);

    @Query(value = """
            SELECT *
            FROM maintenance_completion_anchors
            WHERE equipment_id = :equipmentId
              AND is_deleted = false
              AND (
                    (CAST(:regulationId AS text) IS NOT NULL AND regulation_id = CAST(:regulationId AS uuid))
                 OR (CAST(:ruleId AS text) IS NOT NULL AND equipment_maintenance_rule_id = CAST(:ruleId AS uuid))
              )
            ORDER BY performed_at DESC, updated_at DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<MaintenanceCompletionAnchor> findLatestAnchor(
            @Param("equipmentId") UUID equipmentId,
            @Param("regulationId") UUID regulationId,
            @Param("ruleId") UUID ruleId
    );

    @Query(value = """
            SELECT *
            FROM maintenance_completion_anchors
            WHERE equipment_id = :equipmentId
              AND performed_at >= :historyStart
              AND performed_at <= :asOf
              AND is_deleted = false
            ORDER BY performed_at ASC, id ASC
            LIMIT :limitPlusOne
            """, nativeQuery = true)
    List<MaintenanceCompletionAnchor> findLifecycleAnchors(
            @Param("equipmentId") UUID equipmentId,
            @Param("historyStart") java.time.Instant historyStart,
            @Param("asOf") java.time.Instant asOf,
            @Param("limitPlusOne") int limitPlusOne);

    @Query(value = """
            SELECT DISTINCT work_order_id
            FROM maintenance_completion_anchors
            WHERE equipment_id = :equipmentId
              AND work_order_id IN (:workOrderIds)
              AND performed_at <= :asOf
              AND is_deleted = false
            ORDER BY work_order_id ASC
            """, nativeQuery = true)
    List<UUID> findAnchoredWorkOrderIds(
            @Param("equipmentId") UUID equipmentId,
            @Param("workOrderIds") Collection<UUID> workOrderIds,
            @Param("asOf") java.time.Instant asOf);
}
