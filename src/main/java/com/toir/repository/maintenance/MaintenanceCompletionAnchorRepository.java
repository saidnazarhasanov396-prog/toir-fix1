package com.toir.repository.maintenance;

import com.toir.entity.maintenance.MaintenanceCompletionAnchor;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MaintenanceCompletionAnchorRepository extends JpaRepository<MaintenanceCompletionAnchor, UUID> {

    Optional<MaintenanceCompletionAnchor> findByWorkOrderIdAndIsDeletedFalse(UUID workOrderId);

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
}
