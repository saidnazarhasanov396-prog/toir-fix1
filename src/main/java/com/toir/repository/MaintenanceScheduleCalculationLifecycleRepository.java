package com.toir.repository;

import com.toir.entity.PprPlan;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MaintenanceScheduleCalculationLifecycleRepository
        extends Repository<PprPlan, UUID> {

    @Query(value = """
            SELECT p.*
            FROM ppr_plans p
            LEFT JOIN LATERAL (
              SELECT ar.status
              FROM approval_requests ar
              WHERE ar.is_deleted = false
                AND COALESCE(ar.target_type, ar.document_type) = 'PPR_PLAN'
                AND COALESCE(ar.target_id, ar.document_id) = p.id
                AND COALESCE(ar.action_type, 'APPROVE') = 'APPROVE'
              ORDER BY ar.created_at DESC, ar.id DESC
              LIMIT 1
            ) latest_approval ON true
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
              AND (
                (cast(:lifecycleStatus as text) = 'SAVED'
                 AND p.status IN ('DRAFT', 'GENERATED')
                 AND (latest_approval.status IS NULL
                      OR latest_approval.status NOT IN ('PENDING', 'REJECTED')))
                OR
                (cast(:lifecycleStatus as text) = 'PENDING_APPROVAL'
                 AND p.status IN ('DRAFT', 'GENERATED')
                 AND latest_approval.status = 'PENDING')
                OR
                (cast(:lifecycleStatus as text) = 'REJECTED'
                 AND p.status IN ('DRAFT', 'GENERATED')
                 AND latest_approval.status = 'REJECTED')
                OR
                (cast(:lifecycleStatus as text) IN ('APPROVED', 'IN_PROGRESS', 'CLOSED', 'CANCELLED')
                 AND p.status = cast(:lifecycleStatus as text))
              )
            ORDER BY p.updated_at DESC, p.id ASC
            """,
            countQuery = """
            SELECT count(*)
            FROM ppr_plans p
            LEFT JOIN LATERAL (
              SELECT ar.status
              FROM approval_requests ar
              WHERE ar.is_deleted = false
                AND COALESCE(ar.target_type, ar.document_type) = 'PPR_PLAN'
                AND COALESCE(ar.target_id, ar.document_id) = p.id
                AND COALESCE(ar.action_type, 'APPROVE') = 'APPROVE'
              ORDER BY ar.created_at DESC, ar.id DESC
              LIMIT 1
            ) latest_approval ON true
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
              AND (
                (cast(:lifecycleStatus as text) = 'SAVED'
                 AND p.status IN ('DRAFT', 'GENERATED')
                 AND (latest_approval.status IS NULL
                      OR latest_approval.status NOT IN ('PENDING', 'REJECTED')))
                OR
                (cast(:lifecycleStatus as text) = 'PENDING_APPROVAL'
                 AND p.status IN ('DRAFT', 'GENERATED')
                 AND latest_approval.status = 'PENDING')
                OR
                (cast(:lifecycleStatus as text) = 'REJECTED'
                 AND p.status IN ('DRAFT', 'GENERATED')
                 AND latest_approval.status = 'REJECTED')
                OR
                (cast(:lifecycleStatus as text) IN ('APPROVED', 'IN_PROGRESS', 'CLOSED', 'CANCELLED')
                 AND p.status = cast(:lifecycleStatus as text))
              )
            """,
            nativeQuery = true)
    Page<PprPlan> search(
            @Param("departmentId") UUID departmentId,
            @Param("search") String search,
            @Param("year") Integer year,
            @Param("lifecycleStatus") String lifecycleStatus,
            Pageable pageable
    );

    @Query(value = """
            SELECT
              count(*) AS total,
              count(*) FILTER (
                WHERE p.status IN ('DRAFT', 'GENERATED')
                  AND (latest_approval.status IS NULL
                       OR latest_approval.status NOT IN ('PENDING', 'REJECTED'))
              ) AS saved,
              count(*) FILTER (
                WHERE p.status IN ('DRAFT', 'GENERATED')
                  AND latest_approval.status = 'PENDING'
              ) AS "pendingApproval",
              count(*) FILTER (WHERE p.status = 'APPROVED') AS approved
            FROM ppr_plans p
            LEFT JOIN LATERAL (
              SELECT ar.status
              FROM approval_requests ar
              WHERE ar.is_deleted = false
                AND COALESCE(ar.target_type, ar.document_type) = 'PPR_PLAN'
                AND COALESCE(ar.target_id, ar.document_id) = p.id
                AND COALESCE(ar.action_type, 'APPROVE') = 'APPROVE'
              ORDER BY ar.created_at DESC, ar.id DESC
              LIMIT 1
            ) latest_approval ON true
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
            """,
            nativeQuery = true)
    MaintenanceScheduleCalculationStatsProjection stats(
            @Param("departmentId") UUID departmentId,
            @Param("search") String search,
            @Param("year") Integer year
    );
}
