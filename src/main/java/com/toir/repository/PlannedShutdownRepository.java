package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.PlannedShutdown;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface PlannedShutdownRepository extends JpaRepository<PlannedShutdown, UUID> {
    java.util.Optional<PlannedShutdown> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<PlannedShutdown> findAllByIsDeletedFalse();

    java.util.List<PlannedShutdown> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM planned_shutdowns WHERE department_id = :departmentId AND is_deleted = false ORDER BY start_at DESC", nativeQuery = true)
    List<PlannedShutdown> findAllByDepartmentIdAndIsDeletedFalseOrderByStartAtDesc(@Param("departmentId") UUID departmentId);
}
