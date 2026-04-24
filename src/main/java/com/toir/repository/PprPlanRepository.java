package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.PprPlan;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface PprPlanRepository extends JpaRepository<PprPlan, UUID> {
    @Query(value = "SELECT COUNT(*) > 0 FROM ppr_plans WHERE code = :code AND is_deleted = false", nativeQuery = true)
    boolean existsByCode(@Param("code") String code);

    @Query(value = "SELECT * FROM ppr_plans WHERE year = :year AND month = :month AND is_deleted = false", nativeQuery = true)
    List<PprPlan> findAllByYearAndMonth(@Param("year") int year, @Param("month") int month);

    @Query(value = "SELECT * FROM ppr_plans WHERE department_id = :departmentId AND is_deleted = false", nativeQuery = true)
    List<PprPlan> findAllByDepartmentId(@Param("departmentId") UUID departmentId);
}
