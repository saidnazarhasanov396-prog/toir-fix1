package com.toir.repository.plannedshutdown;

import com.toir.entity.plannedshutdown.PlannedShutdownWorkItem;
import com.toir.enums.PlannedShutdownWorkItemSourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlannedShutdownWorkItemRepository extends JpaRepository<PlannedShutdownWorkItem, UUID> {
    List<PlannedShutdownWorkItem> findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(UUID shutdownId);
    Optional<PlannedShutdownWorkItem> findByIdAndPlannedShutdownIdAndIsDeletedFalse(UUID id, UUID shutdownId);
    boolean existsByPlannedShutdownIdAndSourceTypeAndSourceIdAndIsDeletedFalse(
            UUID shutdownId, PlannedShutdownWorkItemSourceType sourceType, UUID sourceId);
    boolean existsByPlannedShutdownIdAndSourceTypeAndSourceIdAndIdNotAndIsDeletedFalse(
            UUID shutdownId, PlannedShutdownWorkItemSourceType sourceType, UUID sourceId, UUID id);
    boolean existsByPlannedShutdownIdAndOrderNumberAndIsDeletedFalse(UUID shutdownId, Integer orderNumber);
    boolean existsByPlannedShutdownIdAndOrderNumberAndIdNotAndIsDeletedFalse(
            UUID shutdownId, Integer orderNumber, UUID id);
    boolean existsByPlannedShutdownIdAndEquipmentIdAndIsDeletedFalse(UUID shutdownId, UUID equipmentId);
}
