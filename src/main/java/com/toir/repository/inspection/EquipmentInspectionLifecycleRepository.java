package com.toir.repository.inspection;

import com.toir.entity.inspection.InspectionRoundResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EquipmentInspectionLifecycleRepository
        extends JpaRepository<InspectionRoundResult, UUID> {

    @Query(value = """
            SELECT
                result.id AS sourceId,
                round.id AS roundId,
                checkpoint.id AS checkpointId,
                round.status AS roundStatus,
                result.status AS resultStatus,
                round.started_at AS startedAt,
                round.completed_at AS completedAt,
                result.measured_value AS measuredValue,
                result.measured_unit AS measuredUnit,
                checkpoint.expected_min AS expectedMin,
                checkpoint.expected_max AS expectedMax,
                checkpoint.expected_unit AS expectedUnit,
                result.defect_id AS defectId,
                result.updated_at AS sourceUpdatedAt
            FROM inspection_round_results result
            JOIN inspection_rounds round
              ON round.id = result.round_id
             AND round.is_deleted = false
            JOIN inspection_checkpoints checkpoint
              ON checkpoint.id = result.checkpoint_id
             AND checkpoint.is_deleted = false
            WHERE checkpoint.equipment_id = :equipmentId
              AND COALESCE(round.completed_at, round.started_at, result.created_at) >= :historyStart
              AND COALESCE(round.completed_at, round.started_at, result.created_at) <= :asOf
              AND result.is_deleted = false
            ORDER BY COALESCE(round.completed_at, round.started_at, result.created_at) ASC, result.id ASC
            LIMIT :limitPlusOne
            """, nativeQuery = true)
    List<EquipmentInspectionLifecycleProjection> findLifecycleInspections(
            @Param("equipmentId") UUID equipmentId,
            @Param("historyStart") Instant historyStart,
            @Param("asOf") Instant asOf,
            @Param("limitPlusOne") int limitPlusOne);
}
