package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.CalibrationRecord;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface CalibrationRecordRepository extends JpaRepository<CalibrationRecord, UUID> {
    Optional<CalibrationRecord> findByIdAndIsDeletedFalse(UUID id);

    List<CalibrationRecord> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query("""
            SELECT cr FROM CalibrationRecord cr
                        WHERE cr.isDeleted = false
                        AND (CAST(:param AS string) IS NULL OR CAST(:param AS string) = '' OR
                            LOWER(cr.certificateNumber) LIKE LOWER(CONCAT('%', CAST(:param AS string), '%')) OR
                            LOWER(cr.performedBy) LIKE LOWER(CONCAT('%', CAST(:param AS string), '%')))
                        ORDER BY cr.updatedAt DESC
            """)
    List<CalibrationRecord> findAll(
            @Param("param") String search
    );

    List<CalibrationRecord> findAllByIdInAndIsDeletedFalse(Collection<UUID> ids);

    boolean existsByIdAndIsDeletedFalse(UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM calibration_records WHERE equipment_id = :equipmentId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<CalibrationRecord> findAllByEquipmentIdAndIsDeletedFalseOrderByPerformedAtDesc(@Param("equipmentId") UUID equipmentId);

    @Query(value = "SELECT * FROM calibration_records WHERE next_due_at < :date AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<CalibrationRecord> findAllByNextDueAtBeforeAndIsDeletedFalse(@Param("date") LocalDate date);

    @Query(value = "SELECT * FROM calibration_records WHERE result = :result AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<CalibrationRecord> findAllByResultAndIsDeletedFalse(@Param("result") String result);
}
