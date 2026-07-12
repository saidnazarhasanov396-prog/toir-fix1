package com.toir.repository.plannedshutdown;

import com.toir.entity.plannedshutdown.PlannedShutdownProductionReturn;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PlannedShutdownProductionReturnRepository extends JpaRepository<PlannedShutdownProductionReturn, UUID> {
    Optional<PlannedShutdownProductionReturn> findByPlannedShutdownIdAndIsDeletedFalse(UUID shutdownId);
}
