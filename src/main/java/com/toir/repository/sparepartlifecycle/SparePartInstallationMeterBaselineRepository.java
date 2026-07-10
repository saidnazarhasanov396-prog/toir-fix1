package com.toir.repository.sparepartlifecycle;

import com.toir.entity.sparepartlifecycle.SparePartInstallationMeterBaseline;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SparePartInstallationMeterBaselineRepository
        extends JpaRepository<SparePartInstallationMeterBaseline, UUID> {

    List<SparePartInstallationMeterBaseline> findAllByInstallationIdAndIsDeletedFalse(UUID installationId);

    List<SparePartInstallationMeterBaseline> findAllByEquipmentMeterIdAndIsDeletedFalse(UUID equipmentMeterId);
}
