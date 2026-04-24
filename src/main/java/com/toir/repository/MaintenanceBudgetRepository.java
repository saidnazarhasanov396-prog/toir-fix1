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
    @Query(value = "SELECT * FROM maintenance_budgets WHERE year = :year AND is_deleted = false", nativeQuery = true)
    List<MaintenanceBudget> findAllByYear(@Param("year") int year);

    @Query(value = "SELECT * FROM maintenance_budgets WHERE department_id = :departmentId AND year = :year AND is_deleted = false", nativeQuery = true)
    List<MaintenanceBudget> findAllByDepartmentIdAndYear(@Param("departmentId") UUID departmentId, @Param("year") int year);
}
