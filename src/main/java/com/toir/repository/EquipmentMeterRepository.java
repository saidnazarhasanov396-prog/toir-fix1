package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.EquipmentMeter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface EquipmentMeterRepository extends JpaRepository<EquipmentMeter, UUID> {
    java.util.Optional<EquipmentMeter> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<EquipmentMeter> findAllByIsDeletedFalse();

    java.util.List<EquipmentMeter> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM equipment_meters WHERE equipment_id = :equipmentId AND is_active = true AND is_deleted = false", nativeQuery = true)
    List<EquipmentMeter> findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(@Param("equipmentId") UUID equipmentId);

    @Query(value = "SELECT * FROM equipment_meters WHERE equipment_id = :equipmentId AND is_deleted = false", nativeQuery = true)
    List<EquipmentMeter> findAllByEquipmentIdAndIsDeletedFalse(@Param("equipmentId") UUID equipmentId);
}
