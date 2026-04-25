package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.CalibrationRecord;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;


@Repository
public interface CalibrationRecordRepository extends JpaRepository<CalibrationRecord, UUID> {
    java.util.Optional<CalibrationRecord> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<CalibrationRecord> findAllByIsDeletedFalse();

    java.util.List<CalibrationRecord> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM calibration_records WHERE equipment_id = :equipmentId AND is_deleted = false ORDER BY performed_at DESC", nativeQuery = true)
    List<CalibrationRecord> findAllByEquipmentIdAndIsDeletedFalseOrderByPerformedAtDesc(@Param("equipmentId") UUID equipmentId);

    @Query(value = "SELECT * FROM calibration_records WHERE next_due_at < :date AND is_deleted = false", nativeQuery = true)
    List<CalibrationRecord> findAllByNextDueAtBeforeAndIsDeletedFalse(@Param("date") LocalDate date);

    @Query(value = "SELECT * FROM calibration_records WHERE result = :result AND is_deleted = false", nativeQuery = true)
    List<CalibrationRecord> findAllByResultAndIsDeletedFalse(@Param("result") String result);
}
