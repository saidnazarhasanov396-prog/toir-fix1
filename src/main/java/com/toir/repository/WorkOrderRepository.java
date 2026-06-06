package com.toir.repository;

import com.toir.entity.maintenance.WorkOrder;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkType;
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
public interface WorkOrderRepository extends JpaRepository<WorkOrder, UUID> {
    @Query(value = "SELECT * FROM work_orders WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<WorkOrder> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM work_orders WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<WorkOrder> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM work_orders WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<WorkOrder> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM work_orders WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM work_orders WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM work_orders WHERE number = cast(:number as varchar) AND is_deleted = false)", nativeQuery = true)
    boolean existsByNumberAndIsDeletedFalse(@Param("number") String number);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM work_orders WHERE ppr_task_id = cast(:pprTaskId as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByPprTaskIdAndIsDeletedFalse(@Param("pprTaskId") UUID pprTaskId);

    @Query(value = "SELECT * FROM work_orders WHERE ppr_task_id = cast(:pprTaskId as uuid) AND is_deleted = false ORDER BY updated_at DESC LIMIT 1", nativeQuery = true)
    Optional<WorkOrder> findFirstByPprTaskIdAndIsDeletedFalseOrderByUpdatedAtDesc(@Param("pprTaskId") UUID pprTaskId);

    @Query(value = """
            SELECT EXISTS(
                SELECT 1
                FROM work_orders
                WHERE cycle_key = :cycleKey
                  AND status NOT IN ('COMPLETED', 'CLOSED', 'CANCELLED')
                  AND is_deleted = false
            )
            """, nativeQuery = true)
    boolean existsOpenByCycleKey(@Param("cycleKey") String cycleKey);

    @Query(value = "SELECT COUNT(*) FROM work_orders WHERE status = :status AND is_deleted = false", nativeQuery = true)
    long countByStatusAndIsDeletedFalse(@Param("status") String status);

    @Query(value = "SELECT * FROM work_orders WHERE repair_request_id = cast(:repairRequestId as uuid) AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<WorkOrder> findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(
            @Param("repairRequestId") UUID repairRequestId);

    @Query(value = "SELECT * FROM work_orders WHERE defect_id = cast(:defectId as uuid) AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<WorkOrder> findAllByDefectIdAndIsDeletedFalseOrderByUpdatedAtDesc(@Param("defectId") UUID defectId);

    @Query(value = "SELECT * FROM work_orders WHERE repair_request_id IN (:repairRequestIds) AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<WorkOrder> findAllByRepairRequestIdInAndIsDeletedFalseOrderByUpdatedAtDesc(
            @Param("repairRequestIds") Collection<UUID> repairRequestIds);

    @Query(value = "SELECT * FROM work_orders WHERE defect_id IN (:defectIds) AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<WorkOrder> findAllByDefectIdInAndIsDeletedFalseOrderByUpdatedAtDesc(
            @Param("defectIds") Collection<UUID> defectIds);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM work_orders WHERE equipment_node_id = cast(:equipmentNodeId as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByEquipmentNodeIdAndIsDeletedFalse(@Param("equipmentNodeId") UUID equipmentNodeId);

    @Query(value = "SELECT * FROM work_orders WHERE equipment_node_id = cast(:equipmentNodeId as uuid) AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<WorkOrder> findAllByEquipmentNodeIdAndIsDeletedFalseOrderByUpdatedAtDesc(@Param("equipmentNodeId") UUID equipmentNodeId);

    @Query("SELECT w FROM WorkOrder w WHERE w.isDeleted = false " +
            "AND (:status IS NULL OR w.status = :status) " +
            "AND (:departmentId IS NULL OR w.departmentId = :departmentId) " +
            "AND (:equipmentId IS NULL OR w.equipmentId = :equipmentId)")
    List<WorkOrder> search(@Param("status") WorkOrderStatus status,
            @Param("departmentId") UUID departmentId,
            @Param("equipmentId") UUID equipmentId);

    @Query(nativeQuery = true, value = """
            select
                w.id,
                w.created_at,
                w.updated_at,
                w.is_deleted,
                w.number,
                w.title,
                w.equipment_id,
                nullif(to_jsonb(w)->>'equipment_node_id', '')::uuid as equipment_node_id,
                w.department_id,
                nullif(to_jsonb(w)->>'repair_request_id', '')::uuid as repair_request_id,
                nullif(to_jsonb(w)->>'defect_id', '')::uuid as defect_id,
                w.ppr_task_id,
                nullif(to_jsonb(w)->>'maintenance_due_event_id', '')::uuid as maintenance_due_event_id,
                to_jsonb(w)->>'cycle_key' as cycle_key,
                w.contractor_id,
                nullif(to_jsonb(w)->>'brigade_member_id', '')::uuid as brigade_member_id,
                nullif(to_jsonb(w)->>'warehouse_id', '')::uuid as warehouse_id,
                nullif(to_jsonb(w)->>'replacement_equipment_id', '')::uuid as replacement_equipment_id,
                w.status,
                w.type,
                coalesce(nullif(to_jsonb(w)->>'work_type', ''), 'REPAIR') as work_type,
                w.priority,
                w.start_planned_at,
                w.end_planned_at,
                w.started_at,
                w.completed_at,
                w.summary,
                w.result,
                to_jsonb(w)->>'closure_notes' as closure_notes,
                w.created_by_id,
                w.approved_by_id
            from work_orders w
            where
                w.is_deleted = false
                and (cast(:status as varchar) is null or w.status = cast(:status as varchar))
                and (cast(:departmentId as varchar) is null or w.department_id = cast(:departmentId as uuid))
                and (cast(:equipmentId as varchar) is null or w.equipment_id = cast(:equipmentId as uuid))
                and (
                    nullif(trim(cast(:search as varchar)), '') is null
                    or lower(coalesce(to_jsonb(w)->>'number', '')) like lower(concat('%', cast(:search as varchar), '%'))
                    or lower(coalesce(to_jsonb(w)->>'title', '')) like lower(concat('%', cast(:search as varchar), '%'))
                    or lower(coalesce(to_jsonb(w)->>'summary', '')) like lower(concat('%', cast(:search as varchar), '%'))
                    or lower(coalesce(to_jsonb(w)->>'result', '')) like lower(concat('%', cast(:search as varchar), '%'))
                    or lower(coalesce(to_jsonb(w)->>'closure_notes', '')) like lower(concat('%', cast(:search as varchar), '%'))
                )
            order by w.updated_at desc""", countQuery = """
            select count(*)
            from work_orders w
            where
                w.is_deleted = false
                and (cast(:status as varchar) is null or w.status = cast(:status as varchar))
                and (cast(:departmentId as varchar) is null or w.department_id = cast(:departmentId as uuid))
                and (cast(:equipmentId as varchar) is null or w.equipment_id = cast(:equipmentId as uuid))
                and (
                    nullif(trim(cast(:search as varchar)), '') is null
                    or lower(coalesce(to_jsonb(w)->>'number', '')) like lower(concat('%', cast(:search as varchar), '%'))
                    or lower(coalesce(to_jsonb(w)->>'title', '')) like lower(concat('%', cast(:search as varchar), '%'))
                    or lower(coalesce(to_jsonb(w)->>'summary', '')) like lower(concat('%', cast(:search as varchar), '%'))
                    or lower(coalesce(to_jsonb(w)->>'result', '')) like lower(concat('%', cast(:search as varchar), '%'))
                    or lower(coalesce(to_jsonb(w)->>'closure_notes', '')) like lower(concat('%', cast(:search as varchar), '%'))
                )""")
    Page<WorkOrder> searchPaginated(@Param("status") WorkOrderStatus status,
            @Param("departmentId") UUID departmentId,
            @Param("equipmentId") UUID equipmentId,
            @Param("search") String search,
            Pageable pageable);

    @Query(nativeQuery = true, value = """
            select * from work_orders w where
            w.is_deleted = false
            and w.status in ('APPROVED', 'IN_PROGRESS')
            and (cast(:departmentId as varchar) is null or w.department_id = cast(:departmentId as uuid))
            and (cast(:equipmentId as varchar) is null or w.equipment_id = cast(:equipmentId as uuid))
            and (cast(:search as varchar) is null or lower(w.number) like lower(concat('%', cast(:search as varchar), '%'))
            or lower(w.title) like lower(concat('%', cast(:search as varchar), '%'))
            or lower(w.summary) like lower(concat('%', cast(:search as varchar), '%')))
            order by w.updated_at desc""", countQuery = """
            select count(*) from work_orders w where
            w.is_deleted = false
            and w.status in ('APPROVED', 'IN_PROGRESS')
            and (cast(:departmentId as varchar) is null or w.department_id = cast(:departmentId as uuid))
            and (cast(:equipmentId as varchar) is null or w.equipment_id = cast(:equipmentId as uuid))
            and (cast(:search as varchar) is null or lower(w.number) like lower(concat('%', cast(:search as varchar), '%'))
            or lower(w.title) like lower(concat('%', cast(:search as varchar), '%'))
            or lower(w.summary) like lower(concat('%', cast(:search as varchar), '%')))""")
    Page<WorkOrder> searchMobileFeed(@Param("departmentId") UUID departmentId,
            @Param("equipmentId") UUID equipmentId,
            @Param("search") String search,
            Pageable pageable);

    @Query("""
            select case when count(w) > 0 then true else false end
            from WorkOrder w
            where w.isDeleted = false
              and w.workType = :workType
              and w.replacementEquipmentId = :replacementEquipmentId
              and w.status not in :finalStatuses
            """)
    boolean existsActiveReplacementAssignment(@Param("replacementEquipmentId") UUID replacementEquipmentId,
            @Param("workType") WorkType workType,
            @Param("finalStatuses") Collection<WorkOrderStatus> finalStatuses);

    @Query(nativeQuery = true, value = """
                select
                    count(w.id) as totalOrders,
                    count(w.id) filter (where w.status in ('DRAFT', 'PLANNED', 'APPROVED', 'IN_PROGRESS', 'SUSPENDED')) as openOrders,
                    count(w.id) filter (where w.status in ('COMPLETED', 'CLOSED')) as completedOrders,
                    count(w.id) filter (
                        where w.status not in ('COMPLETED', 'CLOSED', 'CANCELLED')
                          and w.end_planned_at is not null
                          and w.end_planned_at < current_timestamp
                    ) as overdueOrders
                from work_orders w
                where w.is_deleted = false
                  and (cast(:status as varchar) is null or w.status = cast(:status as varchar))
                  and (cast(:departmentId as varchar) is null or w.department_id = cast(:departmentId as uuid))
                  and (cast(:equipmentId as varchar) is null or w.equipment_id = cast(:equipmentId as uuid))
                  and (
                      nullif(trim(cast(:search as varchar)), '') is null
                      or lower(coalesce(to_jsonb(w)->>'number', '')) like lower(concat('%', cast(:search as varchar), '%'))
                      or lower(coalesce(to_jsonb(w)->>'title', '')) like lower(concat('%', cast(:search as varchar), '%'))
                      or lower(coalesce(to_jsonb(w)->>'summary', '')) like lower(concat('%', cast(:search as varchar), '%'))
                      or lower(coalesce(to_jsonb(w)->>'result', '')) like lower(concat('%', cast(:search as varchar), '%'))
                      or lower(coalesce(to_jsonb(w)->>'closure_notes', '')) like lower(concat('%', cast(:search as varchar), '%'))
                  )
            """)
    WorkOrderStatsProjection getWorkOrderStats(
            @Param("status") String status,
            @Param("departmentId") UUID departmentId,
            @Param("equipmentId") UUID equipmentId,
            @Param("search") String search);
}
