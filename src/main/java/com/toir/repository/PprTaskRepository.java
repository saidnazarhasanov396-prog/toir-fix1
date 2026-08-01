package com.toir.repository;

import com.toir.entity.PprTask;
import com.toir.enums.PprTaskStatus;
import com.toir.enums.PlanStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface PprTaskRepository extends JpaRepository<PprTask, UUID> {
    @Query(value = """
            SELECT *
            FROM ppr_tasks
            WHERE equipment_id = :equipmentId
              AND scheduled_start <= :planningEnd
              AND due_date >= :historyStart
              AND is_deleted = false
            ORDER BY due_date ASC, id ASC
            LIMIT :limitPlusOne
            """, nativeQuery = true)
    List<PprTask> findLifecycleTasks(
            @Param("equipmentId") UUID equipmentId,
            @Param("historyStart") LocalDateTime historyStart,
            @Param("planningEnd") LocalDateTime planningEnd,
            @Param("limitPlusOne") int limitPlusOne);

    @Query(value = "SELECT * FROM ppr_tasks WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<PprTask> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query("""
            select t
            from PprTask t
            join fetch t.plan p
            where t.id = :id
              and t.isDeleted = false
              and p.isDeleted = false
              and (p.origin = com.toir.enums.PprPlanOrigin.MANUAL
                   or p.status in (com.toir.enums.PlanStatus.APPROVED, com.toir.enums.PlanStatus.IN_PROGRESS, com.toir.enums.PlanStatus.CLOSED, com.toir.enums.PlanStatus.CANCELLED))
            """)
    Optional<PprTask> findByIdAndIsDeletedFalseWithPlan(@Param("id") UUID id);

    @Query(value = "SELECT * FROM ppr_tasks WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<PprTask> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    Optional<PprTask> findBySourceTypeAndSourceKeyAndIsDeletedFalse(String sourceType, String sourceKey);

    @Query(value = """
            select distinct t
            from PprTask t
            join fetch t.plan p
            left join Equipment e on e.id = t.equipmentId and e.isDeleted = false
            where t.isDeleted = false
              and p.isDeleted = false
              and (p.origin = com.toir.enums.PprPlanOrigin.MANUAL
                   or p.status in (com.toir.enums.PlanStatus.APPROVED, com.toir.enums.PlanStatus.IN_PROGRESS, com.toir.enums.PlanStatus.CLOSED, com.toir.enums.PlanStatus.CANCELLED))
              and (:departmentId is null or e.departmentId = :departmentId)
              and (:equipmentId is null or t.equipmentId = :equipmentId)
              and (:status is null or t.status = :status)
              and (
                    :overdue = false
                    or t.status = :overdueStatus
                    or (
                        t.dueDate is not null
                        and t.dueDate < :now
                        and t.status in :dueDateOverdueStatuses
                    )
              )
            order by t.scheduledStart desc, t.id asc
            """,
            countQuery = """
            select count(distinct t.id)
            from PprTask t
            join t.plan p
            left join Equipment e on e.id = t.equipmentId and e.isDeleted = false
            where t.isDeleted = false
              and p.isDeleted = false
              and (p.origin = com.toir.enums.PprPlanOrigin.MANUAL
                   or p.status in (com.toir.enums.PlanStatus.APPROVED, com.toir.enums.PlanStatus.IN_PROGRESS, com.toir.enums.PlanStatus.CLOSED, com.toir.enums.PlanStatus.CANCELLED))
              and (:departmentId is null or e.departmentId = :departmentId)
              and (:equipmentId is null or t.equipmentId = :equipmentId)
              and (:status is null or t.status = :status)
              and (
                    :overdue = false
                    or t.status = :overdueStatus
                    or (
                        t.dueDate is not null
                        and t.dueDate < :now
                        and t.status in :dueDateOverdueStatuses
                    )
              )
            """)
    Page<PprTask> searchTasks(@Param("departmentId") UUID departmentId,
                              @Param("equipmentId") UUID equipmentId,
                              @Param("status") PprTaskStatus status,
                              @Param("overdue") boolean overdue,
                              @Param("now") LocalDateTime now,
                              @Param("overdueStatus") PprTaskStatus overdueStatus,
                              @Param("dueDateOverdueStatuses") Set<PprTaskStatus> dueDateOverdueStatuses,
                              Pageable pageable);

    default Page<PprTask> searchTasks(UUID departmentId,
                                      UUID equipmentId,
                                      PprTaskStatus status,
                                      Pageable pageable) {
        return searchTasks(
                departmentId,
                equipmentId,
                status,
                false,
                LocalDateTime.now(),
                PprTaskStatus.OVERDUE,
                Set.of(PprTaskStatus.PLANNED, PprTaskStatus.APPROVED, PprTaskStatus.IN_PROGRESS),
                pageable
        );
    }

    default Page<PprTask> searchTasks(UUID departmentId,
                                      UUID equipmentId,
                                      PprTaskStatus status,
                                      boolean overdue,
                                      LocalDateTime now,
                                      Pageable pageable) {
        return searchTasks(
                departmentId,
                equipmentId,
                status,
                overdue,
                now,
                PprTaskStatus.OVERDUE,
                Set.of(PprTaskStatus.PLANNED, PprTaskStatus.APPROVED, PprTaskStatus.IN_PROGRESS),
                pageable
        );
    }

    @Query("""
            select distinct t
            from PprTask t
            join fetch t.plan p
            left join Equipment e on e.id = t.equipmentId and e.isDeleted = false
            where t.isDeleted = false
              and p.isDeleted = false
              and (p.origin = com.toir.enums.PprPlanOrigin.MANUAL
                   or p.status in (com.toir.enums.PlanStatus.APPROVED, com.toir.enums.PlanStatus.IN_PROGRESS, com.toir.enums.PlanStatus.CLOSED, com.toir.enums.PlanStatus.CANCELLED))
              and (:departmentId is null or e.departmentId = :departmentId)
              and (:equipmentId is null or t.equipmentId = :equipmentId)
              and (:status is null or t.status = :status)
              and (
                    :overdue = false
                    or t.status = :overdueStatus
                    or (
                        t.dueDate is not null
                        and t.dueDate < :now
                        and t.status in :dueDateOverdueStatuses
                    )
              )
            order by t.scheduledStart asc, t.id asc
            """)
    List<PprTask> searchTasks(@Param("departmentId") UUID departmentId,
                              @Param("equipmentId") UUID equipmentId,
                              @Param("status") PprTaskStatus status,
                              @Param("overdue") boolean overdue,
                              @Param("now") LocalDateTime now,
                              @Param("overdueStatus") PprTaskStatus overdueStatus,
                              @Param("dueDateOverdueStatuses") Set<PprTaskStatus> dueDateOverdueStatuses);

    default List<PprTask> searchTasks(UUID departmentId,
                                      UUID equipmentId,
                                      PprTaskStatus status) {
        return searchTasks(
                departmentId,
                equipmentId,
                status,
                false,
                LocalDateTime.now(),
                PprTaskStatus.OVERDUE,
                Set.of(PprTaskStatus.PLANNED, PprTaskStatus.APPROVED, PprTaskStatus.IN_PROGRESS)
        );
    }

    @Query(value = """
            select distinct t
            from PprTask t
            join fetch t.plan p
            left join Equipment e on e.id = t.equipmentId and e.isDeleted = false
            where t.isDeleted = false
              and p.isDeleted = false
              and (p.origin = com.toir.enums.PprPlanOrigin.MANUAL
                   or p.status in (com.toir.enums.PlanStatus.APPROVED, com.toir.enums.PlanStatus.IN_PROGRESS, com.toir.enums.PlanStatus.CLOSED, com.toir.enums.PlanStatus.CANCELLED))
              and p.status in :planStatuses
              and t.status in :taskStatuses
              and (:departmentId is null or e.departmentId = :departmentId)
              and (:equipmentId is null or t.equipmentId = :equipmentId)
              and (
                    :overdue = false
                    or t.status = :overdueStatus
                    or (
                        t.dueDate is not null
                        and t.dueDate < :now
                        and t.status in :dueDateOverdueStatuses
                    )
              )
            order by t.scheduledStart desc, t.id asc
            """,
            countQuery = """
            select count(distinct t.id)
            from PprTask t
            join t.plan p
            left join Equipment e on e.id = t.equipmentId and e.isDeleted = false
            where t.isDeleted = false
              and p.isDeleted = false
              and (p.origin = com.toir.enums.PprPlanOrigin.MANUAL
                   or p.status in (com.toir.enums.PlanStatus.APPROVED, com.toir.enums.PlanStatus.IN_PROGRESS, com.toir.enums.PlanStatus.CLOSED, com.toir.enums.PlanStatus.CANCELLED))
              and p.status in :planStatuses
              and t.status in :taskStatuses
              and (:departmentId is null or e.departmentId = :departmentId)
              and (:equipmentId is null or t.equipmentId = :equipmentId)
              and (
                    :overdue = false
                    or t.status = :overdueStatus
                    or (
                        t.dueDate is not null
                        and t.dueDate < :now
                        and t.status in :dueDateOverdueStatuses
                    )
              )
            """)
    Page<PprTask> searchVisibleTasks(
            @Param("departmentId") UUID departmentId,
            @Param("equipmentId") UUID equipmentId,
            @Param("taskStatuses") Set<PprTaskStatus> taskStatuses,
            @Param("planStatuses") Set<PlanStatus> planStatuses,
            @Param("overdue") boolean overdue,
            @Param("now") LocalDateTime now,
            @Param("overdueStatus") PprTaskStatus overdueStatus,
            @Param("dueDateOverdueStatuses") Set<PprTaskStatus> dueDateOverdueStatuses,
            Pageable pageable
    );

    @Query("""
            select distinct t
            from PprTask t
            join fetch t.plan p
            left join Equipment e on e.id = t.equipmentId and e.isDeleted = false
            where t.isDeleted = false
              and p.isDeleted = false
              and (p.origin = com.toir.enums.PprPlanOrigin.MANUAL
                   or p.status in (com.toir.enums.PlanStatus.APPROVED, com.toir.enums.PlanStatus.IN_PROGRESS, com.toir.enums.PlanStatus.CLOSED, com.toir.enums.PlanStatus.CANCELLED))
              and p.status in :planStatuses
              and t.status in :taskStatuses
              and (:departmentId is null or e.departmentId = :departmentId)
              and (:equipmentId is null or t.equipmentId = :equipmentId)
              and (
                    :overdue = false
                    or t.status = :overdueStatus
                    or (
                        t.dueDate is not null
                        and t.dueDate < :now
                        and t.status in :dueDateOverdueStatuses
                    )
              )
            order by t.scheduledStart asc, t.id asc
            """)
    List<PprTask> searchVisibleTasks(
            @Param("departmentId") UUID departmentId,
            @Param("equipmentId") UUID equipmentId,
            @Param("taskStatuses") Set<PprTaskStatus> taskStatuses,
            @Param("planStatuses") Set<PlanStatus> planStatuses,
            @Param("overdue") boolean overdue,
            @Param("now") LocalDateTime now,
            @Param("overdueStatus") PprTaskStatus overdueStatus,
            @Param("dueDateOverdueStatuses") Set<PprTaskStatus> dueDateOverdueStatuses
    );

    default List<PprTask> searchTasks(UUID departmentId,
                                      UUID equipmentId,
                                      PprTaskStatus status,
                                      boolean overdue,
                                      LocalDateTime now) {
        return searchTasks(
                departmentId,
                equipmentId,
                status,
                overdue,
                now,
                PprTaskStatus.OVERDUE,
                Set.of(PprTaskStatus.PLANNED, PprTaskStatus.APPROVED, PprTaskStatus.IN_PROGRESS)
        );
    }

    @Query("""
            select count(distinct t.id)
            from PprTask t
            join t.plan p
            left join Equipment e on e.id = t.equipmentId and e.isDeleted = false
            where t.isDeleted = false
              and p.isDeleted = false
              and (p.origin = com.toir.enums.PprPlanOrigin.MANUAL
                   or p.status in (com.toir.enums.PlanStatus.APPROVED, com.toir.enums.PlanStatus.IN_PROGRESS, com.toir.enums.PlanStatus.CLOSED, com.toir.enums.PlanStatus.CANCELLED))
              and (:departmentId is null or e.departmentId = :departmentId)
              and (:equipmentId is null or t.equipmentId = :equipmentId)
              and (:status is null or t.status = :status)
              and (
                    :overdue = false
                    or t.status = :overdueStatus
                    or (
                        t.dueDate is not null
                        and t.dueDate < :now
                        and t.status in :dueDateOverdueStatuses
                    )
              )
            """)
    long countTasks(@Param("departmentId") UUID departmentId,
                    @Param("equipmentId") UUID equipmentId,
                    @Param("status") PprTaskStatus status,
                    @Param("overdue") boolean overdue,
                    @Param("now") LocalDateTime now,
                    @Param("overdueStatus") PprTaskStatus overdueStatus,
                    @Param("dueDateOverdueStatuses") Set<PprTaskStatus> dueDateOverdueStatuses);

    @Query(value = "SELECT * FROM ppr_tasks WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<PprTask> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query("""
            select distinct t
            from PprTask t
            join fetch t.plan p
            where t.id in :ids
              and t.isDeleted = false
              and p.isDeleted = false
              and (p.origin = com.toir.enums.PprPlanOrigin.MANUAL
                   or p.status in (com.toir.enums.PlanStatus.APPROVED, com.toir.enums.PlanStatus.IN_PROGRESS, com.toir.enums.PlanStatus.CLOSED, com.toir.enums.PlanStatus.CANCELLED))
            """)
    List<PprTask> findAllByIdInAndIsDeletedFalseWithPlan(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM ppr_tasks WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = """
            SELECT EXISTS(
                SELECT 1
                FROM ppr_tasks
                WHERE cycle_key = :cycleKey
                  AND status NOT IN ('COMPLETED', 'CANCELLED')
                  AND is_deleted = false
            )
            """, nativeQuery = true)
    boolean existsOpenByCycleKey(@Param("cycleKey") String cycleKey);

    @Query(value = "SELECT COUNT(*) FROM ppr_tasks WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM ppr_tasks WHERE plan_id = cast(:planId as uuid) AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<PprTask> findAllByPlanIdAndIsDeletedFalseOrderByUpdatedAtDesc(@Param("planId") UUID planId);

    long countByPlanIdAndIsDeletedFalse(UUID planId);

    long countByPlanIdAndSourceCalculationItemIdIsNotNullAndIsDeletedFalse(UUID planId);

    @Query(value = """
            SELECT t.*
            FROM ppr_tasks t
            JOIN ppr_plans p ON p.id = t.plan_id
            WHERE t.plan_id = cast(:planId as uuid)
              AND t.is_deleted = false
              AND p.is_deleted = false
              AND (:operationalOnly = false OR t.source_calculation_item_id IS NOT NULL)
            ORDER BY t.scheduled_start ASC NULLS LAST, t.id ASC
            """, countQuery = """
            SELECT count(*)
            FROM ppr_tasks t
            JOIN ppr_plans p ON p.id = t.plan_id
            WHERE t.plan_id = cast(:planId as uuid)
              AND t.is_deleted = false
              AND p.is_deleted = false
              AND (:operationalOnly = false OR t.source_calculation_item_id IS NOT NULL)
            """, nativeQuery = true)
    Page<PprTask> findPageByPlanId(
            @Param("planId") UUID planId,
            @Param("operationalOnly") boolean operationalOnly,
            Pageable pageable);

    @Query("""
            select task
            from PprTask task
            where task.plan.id = :planId
              and task.sourceCalculationItemId in :sourceCalculationItemIds
            """)
    List<PprTask> findAllByPlanIdAndSourceCalculationItemIdIn(
            @Param("planId") UUID planId,
            @Param("sourceCalculationItemIds") Collection<UUID> sourceCalculationItemIds);

    @Query(value = """
            SELECT *
            FROM ppr_tasks
            WHERE plan_id = cast(:planId as uuid)
              AND is_deleted = false
            ORDER BY scheduled_start ASC NULLS LAST, id ASC
            """, nativeQuery = true)
    List<PprTask> findAllByPlanIdAndIsDeletedFalseOrderByScheduledStartAscIdAsc(@Param("planId") UUID planId);

    @Query(value = """
            SELECT task.*
            FROM ppr_tasks task
            JOIN ppr_plans plan ON plan.id = task.plan_id
            WHERE task.is_deleted = false
              AND plan.is_deleted = false
              AND plan.status IN ('APPROVED', 'IN_PROGRESS')
              AND task.status = 'APPROVED'
              AND task.equipment_id IS NOT NULL
              AND task.scheduled_start
                    - (task.work_order_lead_days * INTERVAL '1 day') <= :now
              AND NOT EXISTS (
                    SELECT 1
                    FROM work_orders work_order
                    WHERE work_order.ppr_task_id = task.id
                      AND work_order.is_deleted = false
              )
            ORDER BY task.scheduled_start ASC, task.id ASC
            LIMIT :batchSize
            FOR UPDATE OF task SKIP LOCKED
            """, nativeQuery = true)
    List<PprTask> findDueForWorkOrderGeneration(
            @Param("now") LocalDateTime now,
            @Param("batchSize") int batchSize);

    boolean existsByCode(String code);

    @Query(value = """
            SELECT COALESCE(MAX(CAST(SUBSTRING(code FROM LENGTH(:prefix) + 1) AS BIGINT)), 0)
            FROM ppr_tasks
            WHERE code LIKE CONCAT(:prefix, '%')
              AND SUBSTRING(code FROM LENGTH(:prefix) + 1) ~ '^[0-9]+$'
            """, nativeQuery = true)
    long maxSequenceByCodePrefix(@Param("prefix") String prefix);

    @Query(value = "SELECT COUNT(*) FROM ppr_tasks WHERE status = :status AND is_deleted = false", nativeQuery = true)
    long countByStatusAndIsDeletedFalse(@Param("status") String status);
}
