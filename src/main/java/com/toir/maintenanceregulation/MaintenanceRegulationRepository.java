package com.toir.maintenanceregulation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MaintenanceRegulationRepository extends JpaRepository<MaintenanceRegulation, UUID> {
    boolean existsByCode(String code);
    List<MaintenanceRegulation> findAllByEquipmentTypeIdAndActiveTrue(UUID equipmentTypeId);
}
