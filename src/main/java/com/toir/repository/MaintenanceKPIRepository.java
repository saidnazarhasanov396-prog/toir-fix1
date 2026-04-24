package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.MaintenanceKPI;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;


@Repository
public interface MaintenanceKPIRepository extends JpaRepository<MaintenanceKPI, UUID> {
    List<MaintenanceKPI> findAllByDepartmentIdOrderByPeriodStartDesc(UUID departmentId);
}
