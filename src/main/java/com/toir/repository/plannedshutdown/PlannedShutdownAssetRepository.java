package com.toir.repository.plannedshutdown;

import com.toir.entity.plannedshutdown.PlannedShutdownAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PlannedShutdownAssetRepository extends JpaRepository<PlannedShutdownAsset, UUID> {
    List<PlannedShutdownAsset> findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(UUID plannedShutdownId);
}
