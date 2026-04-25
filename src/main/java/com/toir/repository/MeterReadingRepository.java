package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.MeterReading;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface MeterReadingRepository extends JpaRepository<MeterReading, UUID> {
    java.util.Optional<MeterReading> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<MeterReading> findAllByIsDeletedFalse();

    java.util.List<MeterReading> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM meter_readings WHERE meter_id = :meterId AND is_deleted = false ORDER BY read_at DESC",
            countQuery = "SELECT COUNT(*) FROM meter_readings WHERE meter_id = :meterId AND is_deleted = false",
            nativeQuery = true)
    Page<MeterReading> findAllByMeterIdAndIsDeletedFalseOrderByReadAtDesc(@Param("meterId") UUID meterId, Pageable pageable);

    @Query(value = "SELECT * FROM meter_readings WHERE meter_id = :meterId AND read_at BETWEEN :from AND :to AND is_deleted = false ORDER BY read_at ASC", nativeQuery = true)
    List<MeterReading> findAllByMeterIdAndReadAtBetweenAndIsDeletedFalseOrderByReadAtAsc(@Param("meterId") UUID meterId, @Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "SELECT * FROM meter_readings WHERE meter_id = :meterId AND is_deleted = false ORDER BY read_at DESC LIMIT 1", nativeQuery = true)
    Optional<MeterReading> findTopByMeterIdAndIsDeletedFalseOrderByReadAtDesc(@Param("meterId") UUID meterId);

    @Query(value = "SELECT * FROM meter_readings WHERE equipment_id = :equipmentId AND is_deleted = false ORDER BY read_at DESC", nativeQuery = true)
    List<MeterReading> findAllByEquipmentIdAndIsDeletedFalseOrderByReadAtDesc(@Param("equipmentId") UUID equipmentId);
}
