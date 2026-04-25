package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.enums.ConditionParameter;
import com.toir.entity.ConditionReading;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;


@Repository
public interface ConditionReadingRepository extends JpaRepository<ConditionReading, UUID> {
    java.util.Optional<ConditionReading> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<ConditionReading> findAllByIsDeletedFalse();

    java.util.List<ConditionReading> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM condition_readings WHERE equipment_id = :equipmentId AND is_deleted = false ORDER BY recorded_at DESC", nativeQuery = true)
    List<ConditionReading> findAllByEquipmentIdAndIsDeletedFalseOrderByRecordedAtDesc(@Param("equipmentId") UUID equipmentId);

    @Query(value = "SELECT * FROM condition_readings WHERE equipment_id = :equipmentId AND parameter = :parameter AND is_deleted = false ORDER BY recorded_at DESC", nativeQuery = true)
    List<ConditionReading> findAllByEquipmentIdAndParameterAndIsDeletedFalseOrderByRecordedAtDesc(@Param("equipmentId") UUID equipmentId, @Param("parameter") ConditionParameter parameter);

    @Query(value = "SELECT * FROM condition_readings WHERE recorded_at > :since AND is_deleted = false ORDER BY recorded_at DESC", nativeQuery = true)
    List<ConditionReading> findAllByRecordedAtAfterAndIsDeletedFalseOrderByRecordedAtDesc(@Param("since") Instant since);

    @Query(value = "SELECT * FROM condition_readings WHERE severity = :severity AND is_deleted = false ORDER BY recorded_at DESC", nativeQuery = true)
    List<ConditionReading> findAllBySeverityAndIsDeletedFalseOrderByRecordedAtDesc(@Param("severity") String severity);
}
