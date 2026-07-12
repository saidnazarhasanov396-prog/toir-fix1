package com.toir.repository.plannedshutdown;

import com.toir.entity.plannedshutdown.PlannedShutdownStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlannedShutdownStatusHistoryRepository extends JpaRepository<PlannedShutdownStatusHistory, UUID> {
    List<PlannedShutdownStatusHistory> findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOccurredAtAsc(UUID id);
}
