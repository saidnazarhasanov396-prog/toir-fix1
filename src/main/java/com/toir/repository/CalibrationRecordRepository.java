package com.toir.repository;

import com.toir.entity.equipment.CalibrationRecord;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface CalibrationRecordRepository extends JpaRepository<CalibrationRecord, UUID> {
    @Query(value = "SELECT * FROM calibration_records WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<CalibrationRecord> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM calibration_records WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
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

    @Query(value = "SELECT * FROM calibration_records WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<CalibrationRecord> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM calibration_records WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM calibration_records WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM calibration_records WHERE equipment_id = :equipmentId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<CalibrationRecord> findAllByEquipmentIdAndIsDeletedFalseOrderByPerformedAtDesc(@Param("equipmentId") UUID equipmentId);

    @Query(value = "SELECT * FROM calibration_records WHERE next_due_at < :date AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<CalibrationRecord> findAllByNextDueAtBeforeAndIsDeletedFalse(@Param("date") LocalDate date);

    @Query(value = "SELECT * FROM calibration_records WHERE result = :result AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<CalibrationRecord> findAllByResultAndIsDeletedFalse(@Param("result") String result);
}
