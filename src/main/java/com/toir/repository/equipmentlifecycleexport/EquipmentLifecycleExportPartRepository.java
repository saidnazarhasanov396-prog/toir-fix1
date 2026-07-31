package com.toir.repository.equipmentlifecycleexport;

import com.toir.entity.equipmentlifecycleexport.EquipmentLifecycleExportPart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EquipmentLifecycleExportPartRepository extends JpaRepository<EquipmentLifecycleExportPart, UUID> {
    List<EquipmentLifecycleExportPart> findAllByJobIdOrderByPartNumberAsc(UUID jobId);
    Optional<EquipmentLifecycleExportPart> findByJobIdAndPartNumber(UUID jobId, int partNumber);
    void deleteAllByJobId(UUID jobId);
}
