package com.toir.repository.equipmentlifecycleexport;

import com.toir.entity.equipmentlifecycleexport.EquipmentLifecycleExportArtifact;
import com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportArtifactType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EquipmentLifecycleExportArtifactRepository
        extends JpaRepository<EquipmentLifecycleExportArtifact, UUID> {
    List<EquipmentLifecycleExportArtifact> findAllByJobIdOrderByArtifactTypeAsc(UUID jobId);
    Optional<EquipmentLifecycleExportArtifact> findByJobIdAndArtifactType(
            UUID jobId,
            EquipmentLifecycleExportArtifactType artifactType
    );
    void deleteAllByJobId(UUID jobId);
}
