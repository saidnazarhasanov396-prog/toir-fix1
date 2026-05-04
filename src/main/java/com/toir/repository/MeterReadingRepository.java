package com.toir.repository;

import com.toir.entity.equipment.MeterReading;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface MeterReadingRepository extends JpaRepository<MeterReading, UUID> {
    @Query(value = "SELECT * FROM meter_readings WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<MeterReading> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM meter_readings WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<MeterReading> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM meter_readings WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<MeterReading> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM meter_readings WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM meter_readings WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM meter_readings WHERE meter_id = :meterId AND is_deleted = false ORDER BY updated_at DESC",
            countQuery = "SELECT COUNT(*) FROM meter_readings WHERE meter_id = :meterId AND is_deleted = false",
            nativeQuery = true)
    Page<MeterReading> findAllByMeterIdAndIsDeletedFalseOrderByReadAtDesc(@Param("meterId") UUID meterId, Pageable pageable);

    @Query(value = "SELECT * FROM meter_readings WHERE meter_id = :meterId AND read_at BETWEEN :from AND :to AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<MeterReading> findAllByMeterIdAndReadAtBetweenAndIsDeletedFalseOrderByReadAtAsc(@Param("meterId") UUID meterId, @Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "SELECT * FROM meter_readings WHERE meter_id = :meterId AND is_deleted = false ORDER BY read_at DESC LIMIT 1", nativeQuery = true)
    Optional<MeterReading> findTopByMeterIdAndIsDeletedFalseOrderByReadAtDesc(@Param("meterId") UUID meterId);

    @Query(value = "SELECT * FROM meter_readings WHERE equipment_id = :equipmentId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<MeterReading> findAllByEquipmentIdAndIsDeletedFalseOrderByReadAtDesc(@Param("equipmentId") UUID equipmentId);
}
