package com.toir.repository;
import com.toir.entity.PprPlan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PprPlanRepository extends JpaRepository<PprPlan, UUID> {
    boolean existsByCode(String code);
    List<PprPlan> findAllByYearAndMonth(int year, int month);
    List<PprPlan> findAllByDepartmentId(UUID departmentId);
}
