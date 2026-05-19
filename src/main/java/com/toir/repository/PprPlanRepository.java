package com.toir.repository;

import com.toir.entity.PprPlan;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface PprPlanRepository extends JpaRepository<PprPlan, UUID> {
    Optional<PprPlan> findByIdAndIsDeletedFalse(UUID id);

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

    @Query(value = """
            SELECT COALESCE(MAX(CAST(SUBSTRING(code FROM LENGTH(:prefix) + 1) AS BIGINT)), 0)
            FROM ppr_plans
            WHERE code LIKE CONCAT(:prefix, '%')
              AND SUBSTRING(code FROM LENGTH(:prefix) + 1) ~ '^[0-9]+$'
            """, nativeQuery = true)
    long maxSequenceByCodePrefix(@Param("prefix") String prefix);

    @Query(value = "SELECT * FROM ppr_plans WHERE year = :year AND month = :month AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<PprPlan> findAllByYearAndMonthAndIsDeletedFalse(@Param("year") int year, @Param("month") int month);

    @Query(value = "SELECT * FROM ppr_plans WHERE department_id = :departmentId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<PprPlan> findAllByDepartmentIdAndIsDeletedFalse(@Param("departmentId") UUID departmentId);

    @Query("""
            select p
            from PprPlan p
            where p.isDeleted = false
              and (:year is null or p.year = :year)
              and (:month is null or p.month = :month)
              and (:departmentId is null or p.departmentId = :departmentId)
            order by p.year desc, p.month desc, p.updatedAt desc, p.id asc
            """)
    Page<PprPlan> searchPlans(
            @Param("year") Integer year,
            @Param("month") Integer month,
            @Param("departmentId") UUID departmentId,
            Pageable pageable
    );

    @Query(value = """
            select
                count(distinct p.id) as "totalPlans",
                count(distinct p.id) filter (where p.status = 'DRAFT') as "draftPlans",
                count(distinct p.id) filter (where p.status = 'GENERATED') as "generatedPlans",
                count(distinct p.id) filter (where p.status = 'APPROVED') as "approvedPlans",
                count(t.id) filter (where t.status = 'PLANNED') as "plannedTasks",
                count(t.id) filter (where t.status = 'IN_PROGRESS') as "inProgressTasks",
                count(t.id) filter (where t.status = 'COMPLETED') as "completedTasks"
            from ppr_plans p
            left join ppr_tasks t
                on t.plan_id = p.id
               and t.is_deleted = false
            where p.is_deleted = false
              and (cast(:year as integer) is null or p.year = cast(:year as integer))
              and (cast(:month as integer) is null or p.month = cast(:month as integer))
              and (cast(:departmentId as uuid) is null or p.department_id = cast(:departmentId as uuid))
            """, nativeQuery = true)
    PprPlanStatsProjection getStats(
            @Param("year") Integer year,
            @Param("month") Integer month,
            @Param("departmentId") UUID departmentId
    );
}
