package com.toir.repository;

import com.toir.entity.PprPlan;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface PprPlanRepository extends JpaRepository<PprPlan, UUID> {
    @Query(value = "SELECT * FROM ppr_plans WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<PprPlan> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM ppr_plans WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<PprPlan> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM ppr_plans WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<PprPlan> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM ppr_plans WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM ppr_plans WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT COUNT(*) > 0 FROM ppr_plans WHERE code = :code AND is_deleted = false", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);

    @Query(value = "SELECT * FROM ppr_plans WHERE year = :year AND month = :month AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<PprPlan> findAllByYearAndMonthAndIsDeletedFalse(@Param("year") int year, @Param("month") int month);

    @Query(value = "SELECT * FROM ppr_plans WHERE department_id = :departmentId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<PprPlan> findAllByDepartmentIdAndIsDeletedFalse(@Param("departmentId") UUID departmentId);
}
