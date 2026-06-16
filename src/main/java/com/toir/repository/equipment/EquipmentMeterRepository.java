package com.toir.repository.equipment;

import com.toir.entity.equipment.EquipmentMeter;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface EquipmentMeterRepository extends JpaRepository<EquipmentMeter, UUID> {
    @Query(value = "SELECT * FROM equipment_meters WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<EquipmentMeter> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value =  """
            SELECT em.* FROM equipment_meters em
            LEFT JOIN equipment e ON e.id = em.equipment_id AND e.is_deleted = false
            WHERE em.is_deleted = false
              AND (:metricType IS NULL OR :metricType = '' OR em.meter_type = :metricType)
              AND (CAST(:equipmentId AS text) IS NULL OR em.equipment_id = CAST(:equipmentId AS uuid))
              AND (CAST(:equipmentSearch AS text) IS NULL OR :equipmentSearch = '' OR
                   LOWER(COALESCE(e.code, '')) LIKE LOWER(CONCAT('%', CAST(:equipmentSearch AS text), '%')) OR
                   LOWER(COALESCE(e.name, '')) LIKE LOWER(CONCAT('%', CAST(:equipmentSearch AS text), '%')) OR
                   LOWER(COALESCE(e.inventory_number, '')) LIKE LOWER(CONCAT('%', CAST(:equipmentSearch AS text), '%')) OR
                   LOWER(COALESCE(e.technical_number, '')) LIKE LOWER(CONCAT('%', CAST(:equipmentSearch AS text), '%')) OR
                   LOWER(COALESCE(e.serial_number, '')) LIKE LOWER(CONCAT('%', CAST(:equipmentSearch AS text), '%'))
              )
              AND (CAST(:search AS text) IS NULL OR :search = '' OR
                   LOWER(em.name) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')) OR
                   LOWER(em.unit) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')) OR
                   LOWER(COALESCE(e.code, '')) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')) OR
                   LOWER(COALESCE(e.name, '')) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')) OR
                   LOWER(COALESCE(e.inventory_number, '')) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%'))
              )
            ORDER BY em.updated_at DESC
            """, nativeQuery = true)
    List<EquipmentMeter> findAllByFiltersOrderByUpdatedAtDesc(
            @Param("search") String search,
            @Param("metricType") String meterTypeStr,
            @Param("equipmentId") UUID equipmentId,
            @Param("equipmentSearch") String equipmentSearch
    );

    @Query(value = "SELECT * FROM equipment_meters WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<EquipmentMeter> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM equipment_meters WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM equipment_meters WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM equipment_meters WHERE equipment_id = :equipmentId AND is_active = true AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<EquipmentMeter> findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(@Param("equipmentId") UUID equipmentId);

    @Query(value = "SELECT * FROM equipment_meters WHERE equipment_id IN (:equipmentIds) AND is_active = true AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<EquipmentMeter> findAllByEquipmentIdInAndActiveTrueAndIsDeletedFalse(@Param("equipmentIds") Collection<UUID> equipmentIds);

    @Query(value = "SELECT * FROM equipment_meters WHERE equipment_id = :equipmentId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<EquipmentMeter> findAllByEquipmentIdAndIsDeletedFalse(@Param("equipmentId") UUID equipmentId);

    @Query(value = """
        WITH filtered_meters AS (
            SELECT em.id, em.is_active
            FROM equipment_meters em
            LEFT JOIN equipment e ON e.id = em.equipment_id AND e.is_deleted = false
            WHERE em.is_deleted = false
              AND (:metricType IS NULL OR :metricType = '' OR em.meter_type = :metricType)
              AND (CAST(:equipmentId AS text) IS NULL OR em.equipment_id = CAST(:equipmentId AS uuid))
              AND (CAST(:equipmentSearch AS text) IS NULL OR :equipmentSearch = '' OR
                   LOWER(COALESCE(e.code, '')) LIKE LOWER(CONCAT('%', CAST(:equipmentSearch AS text), '%')) OR
                   LOWER(COALESCE(e.name, '')) LIKE LOWER(CONCAT('%', CAST(:equipmentSearch AS text), '%')) OR
                   LOWER(COALESCE(e.inventory_number, '')) LIKE LOWER(CONCAT('%', CAST(:equipmentSearch AS text), '%')) OR
                   LOWER(COALESCE(e.technical_number, '')) LIKE LOWER(CONCAT('%', CAST(:equipmentSearch AS text), '%')) OR
                   LOWER(COALESCE(e.serial_number, '')) LIKE LOWER(CONCAT('%', CAST(:equipmentSearch AS text), '%'))
              )
              AND (CAST(:search AS text) IS NULL OR :search = '' OR
                   LOWER(em.name) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')) OR
                   LOWER(em.unit) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')) OR
                   LOWER(COALESCE(e.code, '')) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')) OR
                   LOWER(COALESCE(e.name, '')) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')) OR
                   LOWER(COALESCE(e.inventory_number, '')) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%'))
              )
        )
        SELECT 
            (SELECT count(*) FROM filtered_meters) as totalMeters,
            (SELECT count(*) FROM filtered_meters WHERE is_active = true) as activeMeters,
            (SELECT count(r.id) FROM meter_readings r 
             WHERE r.is_deleted = false 
               AND r.meter_id IN (SELECT id FROM filtered_meters)
            ) as totalReadings,
            0 as dueTriggers
        """, nativeQuery = true)
    MeterStatsProjection getMeterStats(
            @Param("search") String search,
            @Param("metricType") String meterTypeStr,
            @Param("equipmentId") UUID equipmentId,
            @Param("equipmentSearch") String equipmentSearch
    );
}
