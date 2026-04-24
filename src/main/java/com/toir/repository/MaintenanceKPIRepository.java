package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.MaintenanceKPI;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface MaintenanceKPIRepository extends JpaRepository<MaintenanceKPI, UUID> {
    @Query(value = "SELECT * FROM maintenance_kpis WHERE department_id = :departmentId AND is_deleted = false ORDER BY period_start DESC", nativeQuery = true)
    List<MaintenanceKPI> findAllByDepartmentIdOrderByPeriodStartDesc(@Param("departmentId") UUID departmentId);
}
