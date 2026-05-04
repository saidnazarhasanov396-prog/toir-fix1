package com.toir.repository.equipment;

import com.toir.entity.equipment.EquipmentKPI;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface EquipmentKPIRepository extends JpaRepository<EquipmentKPI, UUID> {
    @Query(value = "SELECT * FROM equipment_kpis WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<EquipmentKPI> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM equipment_kpis WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<EquipmentKPI> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM equipment_kpis WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<EquipmentKPI> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM equipment_kpis WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM equipment_kpis WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM equipment_kpis WHERE equipment_id = :equipmentId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<EquipmentKPI> findAllByEquipmentIdAndIsDeletedFalseOrderByPeriodStartDesc(@Param("equipmentId") UUID equipmentId);
}
