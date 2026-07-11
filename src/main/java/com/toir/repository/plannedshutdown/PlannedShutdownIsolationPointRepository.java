package com.toir.repository.plannedshutdown;

import com.toir.entity.plannedshutdown.PlannedShutdownIsolationPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlannedShutdownIsolationPointRepository extends JpaRepository<PlannedShutdownIsolationPoint, UUID> {
    List<PlannedShutdownIsolationPoint> findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(UUID shutdownId);
    Optional<PlannedShutdownIsolationPoint> findByIdAndPlannedShutdownIdAndIsDeletedFalse(UUID id, UUID shutdownId);
    boolean existsByPlannedShutdownIdAndLockTagIdentifierAndIsDeletedFalse(UUID shutdownId, String lockTagIdentifier);
    boolean existsByPlannedShutdownIdAndLockTagIdentifierAndIdNotAndIsDeletedFalse(UUID shutdownId, String lockTagIdentifier, UUID id);
}
