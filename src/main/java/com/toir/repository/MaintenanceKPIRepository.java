package com.toir.repository;
import com.toir.entity.MaintenanceKPI;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MaintenanceKPIRepository extends JpaRepository<MaintenanceKPI, UUID> {
    List<MaintenanceKPI> findAllByDepartmentIdOrderByPeriodStartDesc(UUID departmentId);
}
