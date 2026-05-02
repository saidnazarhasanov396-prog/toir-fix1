package com.toir.repository;

import com.toir.entity.EquipmentMeter;
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
            WHERE is_deleted = false
            AND (:metricType IS NULL OR :metricType = '' OR em.meter_type = :metricType)
            AND (CAST(:search AS text) IS NULL OR :search = '' OR
                LOWER(em.name) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')) OR
                LOWER(em.unit) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%'))
            )
            """, nativeQuery = true)
    List<EquipmentMeter> findAllByIsDeletedFalseOrderByUpdatedAtDesc(
            @Param("search")  String search,
            @Param("metricType") String meterTypeStr
    );

    @Query(value = "SELECT * FROM equipment_meters WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<EquipmentMeter> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM equipment_meters WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM equipment_meters WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM equipment_meters WHERE equipment_id = :equipmentId AND is_active = true AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<EquipmentMeter> findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(@Param("equipmentId") UUID equipmentId);

    @Query(value = "SELECT * FROM equipment_meters WHERE equipment_id = :equipmentId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<EquipmentMeter> findAllByEquipmentIdAndIsDeletedFalse(@Param("equipmentId") UUID equipmentId);
}
