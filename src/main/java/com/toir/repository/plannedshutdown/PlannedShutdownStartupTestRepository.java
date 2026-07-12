package com.toir.repository.plannedshutdown;

import com.toir.entity.plannedshutdown.PlannedShutdownStartupTest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlannedShutdownStartupTestRepository extends JpaRepository<PlannedShutdownStartupTest, UUID> {
    List<PlannedShutdownStartupTest> findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(UUID shutdownId);
    Optional<PlannedShutdownStartupTest> findByIdAndPlannedShutdownIdAndIsDeletedFalse(UUID id, UUID shutdownId);
    boolean existsByPlannedShutdownIdAndTestKeyAndIsDeletedFalse(UUID shutdownId, String testKey);
}
