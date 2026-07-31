package com.toir.repository;

import com.toir.entity.equipment.MeterReading;
import com.toir.enums.MeterReadingContext;
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

    @Query(value = "SELECT * FROM meter_readings WHERE repair_request_id = :repairRequestId AND is_deleted = false ORDER BY read_at DESC, created_at DESC", nativeQuery = true)
    List<MeterReading> findAllByRepairRequestIdAndIsDeletedFalseOrderByReadAtDesc(@Param("repairRequestId") UUID repairRequestId);

    @Query("""
            select r
            from MeterReading r
            where r.repairRequestId = :repairRequestId
              and r.readingContext = :readingContext
              and r.isDeleted = false
            order by r.readAt desc, r.createdAt desc
            """)
    List<MeterReading> findAllByRepairRequestIdAndReadingContextAndIsDeletedFalseOrderByReadAtDesc(
            @Param("repairRequestId") UUID repairRequestId,
            @Param("readingContext") MeterReadingContext readingContext);

    @Query(value = "SELECT * FROM meter_readings WHERE repair_request_id IN (:repairRequestIds) AND is_deleted = false ORDER BY read_at DESC, created_at DESC", nativeQuery = true)
    List<MeterReading> findAllByRepairRequestIdInAndIsDeletedFalseOrderByReadAtDesc(@Param("repairRequestIds") Collection<UUID> repairRequestIds);

    @Query(value = """
            SELECT *
            FROM meter_readings r
            WHERE r.is_deleted = false
              AND (
                    cast(:search as varchar) IS NULL
                    OR lower(cast(r.meter_id as varchar)) LIKE lower(concat('%', cast(:search as varchar), '%'))
                    OR lower(cast(r.equipment_id as varchar)) LIKE lower(concat('%', cast(:search as varchar), '%'))
                    OR lower(cast(r.source as varchar)) LIKE lower(concat('%', cast(:search as varchar), '%'))
                    OR lower(cast(r.device_id as varchar)) LIKE lower(concat('%', cast(:search as varchar), '%'))
                    OR lower(cast(r.note as varchar)) LIKE lower(concat('%', cast(:search as varchar), '%'))
                    OR cast(r.value as varchar) LIKE concat('%', cast(:search as varchar), '%')
              )
            ORDER BY
              CASE WHEN :sort = 'createdAt' AND :direction = 'asc' THEN r.created_at END ASC,
              CASE WHEN :sort = 'createdAt' AND :direction = 'desc' THEN r.created_at END DESC,
              CASE WHEN :sort = 'readAt' AND :direction = 'asc' THEN r.read_at END ASC,
              CASE WHEN :sort = 'readAt' AND :direction = 'desc' THEN r.read_at END DESC,
              CASE WHEN :sort = 'value' AND :direction = 'asc' THEN r.value END ASC,
              CASE WHEN :sort = 'value' AND :direction = 'desc' THEN r.value END DESC,
              r.created_at DESC,
              r.id ASC
            """,
            countQuery = """
            SELECT count(*)
            FROM meter_readings r
            WHERE r.is_deleted = false
              AND (
                    cast(:search as varchar) IS NULL
                    OR lower(cast(r.meter_id as varchar)) LIKE lower(concat('%', cast(:search as varchar), '%'))
                    OR lower(cast(r.equipment_id as varchar)) LIKE lower(concat('%', cast(:search as varchar), '%'))
                    OR lower(cast(r.source as varchar)) LIKE lower(concat('%', cast(:search as varchar), '%'))
                    OR lower(cast(r.device_id as varchar)) LIKE lower(concat('%', cast(:search as varchar), '%'))
                    OR lower(cast(r.note as varchar)) LIKE lower(concat('%', cast(:search as varchar), '%'))
                    OR cast(r.value as varchar) LIKE concat('%', cast(:search as varchar), '%')
              )
            """,
            nativeQuery = true)
    Page<MeterReading> searchReadings(
            @Param("search") String search,
            @Param("sort") String sort,
            @Param("direction") String direction,
            Pageable pageable
    );

    @Query(value = """
            SELECT *
            FROM meter_readings
            WHERE equipment_id = :equipmentId
              AND read_at >= :historyStart
              AND read_at <= :asOf
              AND is_deleted = false
            ORDER BY read_at ASC, id ASC
            LIMIT :limitPlusOne
            """, nativeQuery = true)
    List<MeterReading> findLifecycleReadings(
            @Param("equipmentId") UUID equipmentId,
            @Param("historyStart") Instant historyStart,
            @Param("asOf") Instant asOf,
            @Param("limitPlusOne") int limitPlusOne);
}
