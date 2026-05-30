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

    @Query(value = """
            SELECT *
            FROM ppr_plans
            WHERE is_deleted = false
              AND start_date <= :date
              AND end_date >= :date
            ORDER BY updated_at DESC
            """, nativeQuery = true)
    List<PprPlan> findAllActiveOnDate(@Param("date") java.time.LocalDate date);

    @Query(value = "SELECT * FROM ppr_plans WHERE department_id = :departmentId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<PprPlan> findAllByDepartmentIdAndIsDeletedFalse(@Param("departmentId") UUID departmentId);

    @Query(value = """
            SELECT p.*
            FROM ppr_plans p
            WHERE p.is_deleted = false
              AND (cast(:departmentId as uuid) IS NULL OR p.department_id = cast(:departmentId as uuid))
              AND (
                    (cast(:year as integer) IS NULL AND cast(:month as integer) IS NULL AND cast(:day as integer) IS NULL)
                    OR EXISTS (
                        SELECT 1
                        FROM generate_series(p.start_date, p.end_date, interval '1 day') AS d(value)
                        WHERE (cast(:year as integer) IS NULL OR extract(year from d.value)::integer = cast(:year as integer))
                          AND (cast(:month as integer) IS NULL OR extract(month from d.value)::integer = cast(:month as integer))
                          AND (cast(:day as integer) IS NULL OR extract(day from d.value)::integer = cast(:day as integer))
                    )
              )
            ORDER BY p.start_date DESC, p.updated_at DESC, p.id ASC
            """,
            countQuery = """
            SELECT count(*)
            FROM ppr_plans p
            WHERE p.is_deleted = false
              AND (cast(:departmentId as uuid) IS NULL OR p.department_id = cast(:departmentId as uuid))
              AND (
                    (cast(:year as integer) IS NULL AND cast(:month as integer) IS NULL AND cast(:day as integer) IS NULL)
                    OR EXISTS (
                        SELECT 1
                        FROM generate_series(p.start_date, p.end_date, interval '1 day') AS d(value)
                        WHERE (cast(:year as integer) IS NULL OR extract(year from d.value)::integer = cast(:year as integer))
                          AND (cast(:month as integer) IS NULL OR extract(month from d.value)::integer = cast(:month as integer))
                          AND (cast(:day as integer) IS NULL OR extract(day from d.value)::integer = cast(:day as integer))
                    )
              )
            """,
            nativeQuery = true)
    Page<PprPlan> searchPlans(
            @Param("year") Integer year,
            @Param("month") Integer month,
            @Param("day") Integer day,
            @Param("departmentId") UUID departmentId,
            Pageable pageable
    );

    @Query(value = """
            SELECT p.*
            FROM ppr_plans p
            WHERE p.is_deleted = false
              AND (cast(:departmentId as uuid) IS NULL OR p.department_id = cast(:departmentId as uuid))
              AND (
                    (cast(:year as integer) IS NULL AND cast(:month as integer) IS NULL AND cast(:day as integer) IS NULL)
                    OR EXISTS (
                        SELECT 1
                        FROM generate_series(p.start_date, p.end_date, interval '1 day') AS d(value)
                        WHERE (cast(:year as integer) IS NULL OR extract(year from d.value)::integer = cast(:year as integer))
                          AND (cast(:month as integer) IS NULL OR extract(month from d.value)::integer = cast(:month as integer))
                          AND (cast(:day as integer) IS NULL OR extract(day from d.value)::integer = cast(:day as integer))
                    )
              )
            ORDER BY p.start_date DESC, p.updated_at DESC, p.id ASC
            """,
            nativeQuery = true)
    List<PprPlan> searchPlans(
            @Param("year") Integer year,
            @Param("month") Integer month,
            @Param("day") Integer day,
            @Param("departmentId") UUID departmentId
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
              and (cast(:departmentId as uuid) is null or p.department_id = cast(:departmentId as uuid))
              and (
                    (cast(:year as integer) is null and cast(:month as integer) is null and cast(:day as integer) is null)
                    or exists (
                        select 1
                        from generate_series(p.start_date, p.end_date, interval '1 day') as d(value)
                        where (cast(:year as integer) is null or extract(year from d.value)::integer = cast(:year as integer))
                          and (cast(:month as integer) is null or extract(month from d.value)::integer = cast(:month as integer))
                          and (cast(:day as integer) is null or extract(day from d.value)::integer = cast(:day as integer))
                    )
              )
            """, nativeQuery = true)
    PprPlanStatsProjection getStats(
            @Param("year") Integer year,
            @Param("month") Integer month,
            @Param("day") Integer day,
            @Param("departmentId") UUID departmentId
    );

    @Query(value = """
            SELECT
                p.id AS "planId",
                COUNT(t.id) AS "taskCount"
            FROM ppr_plans p
            LEFT JOIN ppr_tasks t
                ON t.plan_id = p.id
               AND t.is_deleted = false
            WHERE p.id IN (:planIds)
              AND p.is_deleted = false
            GROUP BY p.id
            """, nativeQuery = true)
    List<PprPlanTaskCountProjection> countTasksByPlanIds(@Param("planIds") Collection<UUID> planIds);
}
