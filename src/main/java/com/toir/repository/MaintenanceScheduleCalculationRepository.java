package com.toir.repository;

import com.toir.entity.PprPlan;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MaintenanceScheduleCalculationRepository extends JpaRepository<PprPlan, UUID> {

    @Query(value = """
            SELECT p.*
            FROM ppr_plans p
            WHERE p.is_deleted = false
              AND p.origin = 'MAINTENANCE_SCHEDULE'
              AND (cast(:departmentId as uuid) IS NULL
                   OR p.department_id = cast(:departmentId as uuid))
              AND (cast(:search as text) IS NULL
                   OR lower(p.code) LIKE lower(concat('%', cast(:search as text), '%'))
                   OR lower(p.name) LIKE lower(concat('%', cast(:search as text), '%')))
              AND (cast(:year as integer) IS NULL
                   OR (
                     cast(extract(year from p.start_date) as integer) <= cast(:year as integer)
                     AND cast(extract(year from p.end_date) as integer) >= cast(:year as integer)
                   ))
              AND (cast(:status as text) IS NULL OR p.status = cast(:status as text))
            ORDER BY p.updated_at DESC, p.id ASC
            """,
            countQuery = """
            SELECT count(*)
            FROM ppr_plans p
            WHERE p.is_deleted = false
              AND p.origin = 'MAINTENANCE_SCHEDULE'
              AND (cast(:departmentId as uuid) IS NULL
                   OR p.department_id = cast(:departmentId as uuid))
              AND (cast(:search as text) IS NULL
                   OR lower(p.code) LIKE lower(concat('%', cast(:search as text), '%'))
                   OR lower(p.name) LIKE lower(concat('%', cast(:search as text), '%')))
              AND (cast(:year as integer) IS NULL
                   OR (
                     cast(extract(year from p.start_date) as integer) <= cast(:year as integer)
                     AND cast(extract(year from p.end_date) as integer) >= cast(:year as integer)
                   ))
              AND (cast(:status as text) IS NULL OR p.status = cast(:status as text))
            """,
            nativeQuery = true)
    Page<PprPlan> search(
            @Param("departmentId") UUID departmentId,
            @Param("search") String search,
            @Param("year") Integer year,
            @Param("status") String status,
            Pageable pageable
    );
}
