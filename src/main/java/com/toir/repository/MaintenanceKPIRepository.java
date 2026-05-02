package com.toir.repository;

import com.toir.entity.MaintenanceKPI;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface MaintenanceKPIRepository extends JpaRepository<MaintenanceKPI, UUID> {
    @Query(value = "SELECT * FROM maintenance_kpis WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<MaintenanceKPI> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM maintenance_kpis WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<MaintenanceKPI> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM maintenance_kpis WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<MaintenanceKPI> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM maintenance_kpis WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM maintenance_kpis WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM maintenance_kpis WHERE department_id = :departmentId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<MaintenanceKPI> findAllByDepartmentIdAndIsDeletedFalseOrderByPeriodStartDesc(@Param("departmentId") UUID departmentId);
}
