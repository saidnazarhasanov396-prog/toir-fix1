package com.toir.repository;

import com.toir.entity.ConditionReading;
import com.toir.enums.ConditionParameter;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface ConditionReadingRepository extends JpaRepository<ConditionReading, UUID> {
    @Query(value = "SELECT * FROM condition_readings WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<ConditionReading> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM condition_readings WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<ConditionReading> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM condition_readings WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<ConditionReading> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM condition_readings WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM condition_readings WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM condition_readings WHERE equipment_id = :equipmentId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<ConditionReading> findAllByEquipmentIdAndIsDeletedFalseOrderByRecordedAtDesc(@Param("equipmentId") UUID equipmentId);

    @Query(value = "SELECT * FROM condition_readings WHERE equipment_id = :equipmentId AND parameter = :parameter AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<ConditionReading> findAllByEquipmentIdAndParameterAndIsDeletedFalseOrderByRecordedAtDesc(@Param("equipmentId") UUID equipmentId, @Param("parameter") ConditionParameter parameter);

    @Query(value = "SELECT * FROM condition_readings WHERE recorded_at > :since AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<ConditionReading> findAllByRecordedAtAfterAndIsDeletedFalseOrderByRecordedAtDesc(@Param("since") Instant since);

    @Query(value = "SELECT * FROM condition_readings WHERE severity = :severity AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<ConditionReading> findAllBySeverityAndIsDeletedFalseOrderByRecordedAtDesc(@Param("severity") String severity);

    @Query(value = """
            SELECT COUNT(cr.*) FROM condition_readings cr
            JOIN equipment e ON cr.equipment_id = e.id
            WHERE cr.severity IN (:severities)
            AND cr.is_deleted = false
            AND e.is_deleted = false
            AND (:departmentId IS NULL OR e.department_id = :departmentId)
            """, nativeQuery = true)
    long countBySeveritiesAndDepartment(@Param("severities") List<String> severities, @Param("departmentId") UUID departmentId);

    @Query(value = """
            SELECT *
            FROM condition_readings
            WHERE equipment_id = :equipmentId
              AND recorded_at >= :historyStart
              AND recorded_at <= :asOf
              AND is_deleted = false
            ORDER BY recorded_at ASC, id ASC
            LIMIT :limitPlusOne
            """, nativeQuery = true)
    List<ConditionReading> findLifecycleReadings(
            @Param("equipmentId") UUID equipmentId,
            @Param("historyStart") Instant historyStart,
            @Param("asOf") Instant asOf,
            @Param("limitPlusOne") int limitPlusOne);
}
