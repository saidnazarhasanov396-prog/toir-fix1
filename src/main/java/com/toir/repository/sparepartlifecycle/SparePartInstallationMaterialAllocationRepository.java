package com.toir.repository.sparepartlifecycle;

import com.toir.entity.sparepartlifecycle.SparePartInstallationMaterialAllocation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SparePartInstallationMaterialAllocationRepository
        extends JpaRepository<SparePartInstallationMaterialAllocation, UUID> {

    boolean existsByRepairMaterialUsageIdAndIsDeletedFalse(UUID repairMaterialUsageId);

    List<SparePartInstallationMaterialAllocation> findAllByInstallationIdAndIsDeletedFalse(UUID installationId);
}
