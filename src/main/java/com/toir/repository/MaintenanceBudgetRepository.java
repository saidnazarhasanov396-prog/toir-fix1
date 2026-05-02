package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.MaintenanceBudget;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface MaintenanceBudgetRepository extends JpaRepository<MaintenanceBudget, UUID> {
    java.util.Optional<MaintenanceBudget> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<MaintenanceBudget> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    java.util.List<MaintenanceBudget> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM maintenance_budgets WHERE year = :year AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<MaintenanceBudget> findAllByYearAndIsDeletedFalse(@Param("year") int year);

    @Query(value = "SELECT * FROM maintenance_budgets WHERE department_id = :departmentId AND year = :year AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<MaintenanceBudget> findAllByDepartmentIdAndYearAndIsDeletedFalse(@Param("departmentId") UUID departmentId, @Param("year") int year);
}
