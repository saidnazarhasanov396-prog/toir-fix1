package com.toir.repository.plannedshutdown;

import com.toir.entity.plannedshutdown.PlannedShutdownReadinessItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlannedShutdownReadinessItemRepository extends JpaRepository<PlannedShutdownReadinessItem, UUID> {
    List<PlannedShutdownReadinessItem> findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(UUID shutdownId);
    Optional<PlannedShutdownReadinessItem> findByIdAndPlannedShutdownIdAndIsDeletedFalse(UUID id, UUID shutdownId);
    boolean existsByPlannedShutdownIdAndReadinessKeyAndIsDeletedFalse(UUID shutdownId, String readinessKey);
    boolean existsByPlannedShutdownIdAndReadinessKeyAndIdNotAndIsDeletedFalse(UUID shutdownId, String readinessKey, UUID id);
}
