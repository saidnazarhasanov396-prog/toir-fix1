package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.MaintenanceBudget;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;


@Repository
public interface MaintenanceBudgetRepository extends JpaRepository<MaintenanceBudget, UUID> {
    List<MaintenanceBudget> findAllByYear(int year);
    List<MaintenanceBudget> findAllByDepartmentIdAndYear(UUID departmentId, int year);
}
