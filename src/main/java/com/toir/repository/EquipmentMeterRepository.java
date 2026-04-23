package com.toir.repository;
import com.toir.entity.EquipmentMeter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EquipmentMeterRepository extends JpaRepository<EquipmentMeter, UUID> {
    List<EquipmentMeter> findAllByEquipmentIdAndActiveTrue(UUID equipmentId);
    List<EquipmentMeter> findAllByEquipmentId(UUID equipmentId);
}
