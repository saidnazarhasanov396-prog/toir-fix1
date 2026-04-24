package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.EquipmentMeter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;


@Repository
public interface EquipmentMeterRepository extends JpaRepository<EquipmentMeter, UUID> {
    List<EquipmentMeter> findAllByEquipmentIdAndActiveTrue(UUID equipmentId);
    List<EquipmentMeter> findAllByEquipmentId(UUID equipmentId);
}
