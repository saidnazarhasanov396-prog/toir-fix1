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
    @Query(value = "SELECT * FROM equipment_meters WHERE equipment_id = :equipmentId AND is_active = true AND is_deleted = false", nativeQuery = true)
    List<EquipmentMeter> findAllByEquipmentIdAndActiveTrue(@Param("equipmentId") UUID equipmentId);

    @Query(value = "SELECT * FROM equipment_meters WHERE equipment_id = :equipmentId AND is_deleted = false", nativeQuery = true)
    List<EquipmentMeter> findAllByEquipmentId(@Param("equipmentId") UUID equipmentId);
}
