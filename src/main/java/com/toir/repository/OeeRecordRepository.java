package com.toir.repository;

import com.toir.entity.OeeRecord;
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
public interface OeeRecordRepository extends JpaRepository<OeeRecord, UUID> {
    @Query(value = "SELECT * FROM oee_records WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<OeeRecord> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM oee_records WHERE is_deleted = false ORDER BY shift_start DESC", nativeQuery = true)
    List<OeeRecord> findAllByIsDeletedFalseOrderByShiftStartDesc();

    @Query(value = "SELECT * FROM oee_records WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<OeeRecord> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM oee_records WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM oee_records WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM oee_records WHERE equipment_id = :equipmentId AND is_deleted = false ORDER BY shift_start DESC", nativeQuery = true)
    List<OeeRecord> findAllByEquipmentIdAndIsDeletedFalseOrderByShiftStartDesc(@Param("equipmentId") UUID equipmentId);

    @Query(value = "SELECT * FROM oee_records WHERE equipment_id = :equipmentId AND shift_start BETWEEN :from AND :to AND is_deleted = false ORDER BY shift_start ASC", nativeQuery = true)
    List<OeeRecord> findAllByEquipmentIdAndShiftStartBetweenAndIsDeletedFalseOrderByShiftStartAsc(
            @Param("equipmentId") UUID equipmentId, @Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "SELECT * FROM oee_records WHERE equipment_id IN (:equipmentIds) AND is_deleted = false ORDER BY shift_start DESC", nativeQuery = true)
    List<OeeRecord> findAllByEquipmentIdInAndIsDeletedFalseOrderByShiftStartDesc(@Param("equipmentIds") Collection<UUID> equipmentIds);

    @Query(value = "SELECT * FROM oee_records WHERE equipment_id IN (:equipmentIds) AND shift_start BETWEEN :from AND :to AND is_deleted = false ORDER BY shift_start ASC", nativeQuery = true)
    List<OeeRecord> findAllByEquipmentIdInAndShiftStartBetweenAndIsDeletedFalseOrderByShiftStartAsc(
            @Param("equipmentIds") Collection<UUID> equipmentIds, @Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "SELECT * FROM oee_records WHERE shift_start BETWEEN :from AND :to AND is_deleted = false ORDER BY shift_start ASC", nativeQuery = true)
    List<OeeRecord> findAllByShiftStartBetweenAndIsDeletedFalse(@Param("from") Instant from, @Param("to") Instant to);
}
