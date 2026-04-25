package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.OeeRecord;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;


@Repository
public interface OeeRecordRepository extends JpaRepository<OeeRecord, UUID> {
    java.util.Optional<OeeRecord> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<OeeRecord> findAllByIsDeletedFalse();

    java.util.List<OeeRecord> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM oee_records WHERE equipment_id = :equipmentId AND is_deleted = false ORDER BY shift_start DESC", nativeQuery = true)
    List<OeeRecord> findAllByEquipmentIdAndIsDeletedFalseOrderByShiftStartDesc(@Param("equipmentId") UUID equipmentId);

    @Query(value = "SELECT * FROM oee_records WHERE equipment_id = :equipmentId AND shift_start BETWEEN :from AND :to AND is_deleted = false ORDER BY shift_start ASC", nativeQuery = true)
    List<OeeRecord> findAllByEquipmentIdAndShiftStartBetweenAndIsDeletedFalseOrderByShiftStartAsc(
            @Param("equipmentId") UUID equipmentId, @Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "SELECT * FROM oee_records WHERE shift_start BETWEEN :from AND :to AND is_deleted = false", nativeQuery = true)
    List<OeeRecord> findAllByShiftStartBetweenAndIsDeletedFalse(@Param("from") Instant from, @Param("to") Instant to);
}
