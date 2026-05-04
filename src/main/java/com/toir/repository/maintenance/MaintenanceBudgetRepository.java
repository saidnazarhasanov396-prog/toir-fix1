package com.toir.repository.maintenance;

import com.toir.entity.projects.MaintenanceBudget;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface MaintenanceBudgetRepository extends JpaRepository<MaintenanceBudget, UUID> {
    @Query(value = "SELECT * FROM maintenance_budgets WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<MaintenanceBudget> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM maintenance_budgets WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<MaintenanceBudget> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM maintenance_budgets WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<MaintenanceBudget> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM maintenance_budgets WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM maintenance_budgets WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM maintenance_budgets WHERE year = :year AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<MaintenanceBudget> findAllByYearAndIsDeletedFalse(@Param("year") int year);

    @Query(value = "SELECT * FROM maintenance_budgets WHERE department_id = :departmentId AND year = :year AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<MaintenanceBudget> findAllByDepartmentIdAndYearAndIsDeletedFalse(@Param("departmentId") UUID departmentId, @Param("year") int year);
}
