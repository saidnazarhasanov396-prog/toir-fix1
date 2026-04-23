package com.toir.repository;
import com.toir.entity.EquipmentKPI;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EquipmentKPIRepository extends JpaRepository<EquipmentKPI, UUID> {
    List<EquipmentKPI> findAllByEquipmentIdOrderByPeriodStartDesc(UUID equipmentId);
}
