package com.toir.budget;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MaintenanceBudgetRepository extends JpaRepository<MaintenanceBudget, UUID> {
    List<MaintenanceBudget> findAllByYear(int year);
    List<MaintenanceBudget> findAllByDepartmentIdAndYear(UUID departmentId, int year);
}
