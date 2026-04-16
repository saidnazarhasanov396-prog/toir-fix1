package com.toir.equipmentkpi;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EquipmentKPIRepository extends JpaRepository<EquipmentKPI, UUID> {
    List<EquipmentKPI> findAllByEquipmentIdOrderByPeriodStartDesc(UUID equipmentId);
}
