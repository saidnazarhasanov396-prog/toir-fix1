package com.toir.plannedshutdown;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlannedShutdownRepository extends JpaRepository<PlannedShutdown, UUID> {
    List<PlannedShutdown> findAllByDepartmentIdOrderByStartAtDesc(UUID departmentId);
}
