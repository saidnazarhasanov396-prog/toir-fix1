package com.toir.repository.plannedshutdown;

import com.toir.entity.plannedshutdown.PlannedShutdownClosureSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PlannedShutdownClosureSnapshotRepository extends JpaRepository<PlannedShutdownClosureSnapshot, UUID> {
    Optional<PlannedShutdownClosureSnapshot> findByPlannedShutdownIdAndIsDeletedFalse(UUID shutdownId);
}
