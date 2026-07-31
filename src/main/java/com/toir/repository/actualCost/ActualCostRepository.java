package com.toir.repository.actualCost;

import com.toir.entity.projects.ActualCost;
import com.toir.enums.ActualCostSourceType;
import com.toir.enums.ActualCostStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface ActualCostRepository extends JpaRepository<ActualCost, UUID> {
    @Query(value = "SELECT * FROM actual_costs WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<ActualCost> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM actual_costs WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<ActualCost> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM actual_costs WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<ActualCost> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM actual_costs WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM actual_costs WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM actual_costs WHERE work_order_id = cast(:workOrderId as uuid) AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<ActualCost> findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(@Param("workOrderId") UUID workOrderId);

    @Query(value = """
            SELECT *
            FROM actual_costs
            WHERE work_order_id IN (:workOrderIds)
              AND is_deleted = false
            ORDER BY updated_at DESC
            """, nativeQuery = true)
    List<ActualCost> findAllByWorkOrderIdInAndIsDeletedFalseOrderByUpdatedAtDesc(
            @Param("workOrderIds") Collection<UUID> workOrderIds
    );

    @Query(value = """
            SELECT DISTINCT ac.*
            FROM actual_costs ac
            LEFT JOIN work_orders wo
              ON wo.id = ac.work_order_id
             AND wo.is_deleted = false
            WHERE ac.is_deleted = false
              AND (
                    wo.repair_request_id = cast(:repairRequestId as uuid)
                    OR ac.repair_request_id = cast(:repairRequestId as uuid)
                    OR (
                        ac.source_type = 'REPAIR_REQUEST'
                        AND ac.source_id = cast(:repairRequestId as uuid)
                    )
              )
            ORDER BY ac.cost_date DESC, ac.id ASC
            """, nativeQuery = true)
    List<ActualCost> findAllForRepairRequest(@Param("repairRequestId") UUID repairRequestId);

    @Query(value = """
            SELECT *
            FROM actual_costs
            WHERE contractor_work_id = cast(:contractorWorkId as uuid)
              AND is_deleted = false
            ORDER BY updated_at DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<ActualCost> findTopByContractorWorkIdAndIsDeletedFalseOrderByUpdatedAtDesc(
            @Param("contractorWorkId") UUID contractorWorkId
    );

    @Query(value = """
            SELECT EXISTS(
                SELECT 1
                FROM actual_costs
                WHERE contractor_work_id = cast(:contractorWorkId as uuid)
                  AND is_deleted = false
            )
            """, nativeQuery = true)
    boolean existsByContractorWorkIdAndIsDeletedFalse(@Param("contractorWorkId") UUID contractorWorkId);

    @Query(value = """
            SELECT *
            FROM actual_costs
            WHERE contractor_work_id IN (:contractorWorkIds)
              AND is_deleted = false
            ORDER BY updated_at DESC
            """, nativeQuery = true)
    List<ActualCost> findAllByContractorWorkIdInAndIsDeletedFalseOrderByUpdatedAtDesc(
            @Param("contractorWorkIds") Collection<UUID> contractorWorkIds
    );

    Optional<ActualCost> findTopBySourceTypeAndSourceIdAndIsDeletedFalseOrderByUpdatedAtDesc(
            ActualCostSourceType sourceType,
            UUID sourceId
    );

    @Query(value = """
            SELECT ac.*
            FROM actual_costs ac
            LEFT JOIN work_orders wo ON wo.id = ac.work_order_id AND wo.is_deleted = false
            LEFT JOIN contractor_works cw ON cw.id = ac.contractor_work_id AND cw.is_deleted = false
            LEFT JOIN contractors c ON c.id = cw.contractor_id AND c.is_deleted = false
            LEFT JOIN counteragents ca ON ca.id = cw.counteragent_id AND ca.is_deleted = false
            LEFT JOIN cost_categories cc ON cc.id = ac.cost_category_id AND cc.is_deleted = false
            WHERE ac.is_deleted = false
              AND (CAST(:workOrderId AS text) IS NULL OR ac.work_order_id = CAST(:workOrderId AS uuid))
              AND (CAST(:search AS text) IS NULL OR :search = '' OR
                   LOWER(COALESCE(wo.number, '')) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')) OR
                   LOWER(COALESCE(wo.title, '')) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')) OR
                   LOWER(COALESCE(ca.code, c.code, '')) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')) OR
                   LOWER(COALESCE(ca.name, c.name, '')) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')) OR
                   LOWER(COALESCE(cc.code, '')) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')) OR
                   LOWER(COALESCE(cc.name, '')) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')) OR
                   LOWER(COALESCE(ac.notes, '')) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')) OR
                   LOWER(COALESCE(ac.review_comment, '')) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')) OR
                   LOWER(COALESCE(CAST(ac.status AS text), '')) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')) OR
                   CAST(ac.amount AS text) LIKE CONCAT('%', CAST(:search AS text), '%') OR
                   CAST(ac.cost_date AS text) LIKE CONCAT('%', CAST(:search AS text), '%')
              )
            ORDER BY ac.updated_at DESC
            """, nativeQuery = true)
    List<ActualCost> findAllByFiltersOrderByUpdatedAtDesc(@Param("workOrderId") UUID workOrderId,
                                                           @Param("search") String search);

    @Query(value = "SELECT * FROM actual_costs WHERE status = :#{#status.name()} AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<ActualCost> findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(@Param("status") ActualCostStatus status);

    @Query(value = """
            SELECT COALESCE(SUM(ac.amount), 0)
            FROM actual_costs ac
            WHERE ac.budget_line_id = cast(:budgetLineId as uuid)
              AND ac.status = cast(:status as varchar)
              AND ac.is_deleted = false
            """, nativeQuery = true)
    double sumAmountByBudgetLineIdAndStatusAndIsDeletedFalse(@Param("budgetLineId") UUID budgetLineId,
                                                              @Param("status") ActualCostStatus status);

    @Query(value = """
            SELECT COALESCE(SUM(ac.amount), 0)
            FROM actual_costs ac
            LEFT JOIN work_orders wo ON wo.id = ac.work_order_id
            LEFT JOIN repair_requests rr ON rr.id = ac.repair_request_id
            WHERE ac.is_deleted = false
              AND (
                  (wo.is_deleted = false AND wo.equipment_id = CAST(:equipmentId AS uuid))
                  OR
                  (rr.is_deleted = false AND rr.equipment_id = CAST(:equipmentId AS uuid))
              )
            """, nativeQuery = true)
    double sumAmountByEquipmentId(@Param("equipmentId") UUID equipmentId);

    @Query(value = """
            SELECT DISTINCT ac.*
            FROM actual_costs ac
            LEFT JOIN work_orders wo
              ON wo.id = ac.work_order_id
             AND wo.is_deleted = false
            LEFT JOIN repair_requests rr
              ON rr.id = ac.repair_request_id
             AND rr.is_deleted = false
            WHERE ac.is_deleted = false
              AND ac.cost_date >= :historyStart
              AND ac.cost_date <= :asOf
              AND (
                    wo.equipment_id = :equipmentId
                 OR rr.equipment_id = :equipmentId
              )
            ORDER BY ac.cost_date ASC, ac.id ASC
            LIMIT :limitPlusOne
            """, nativeQuery = true)
    List<ActualCost> findLifecycleCosts(
            @Param("equipmentId") UUID equipmentId,
            @Param("historyStart") java.time.Instant historyStart,
            @Param("asOf") java.time.Instant asOf,
            @Param("limitPlusOne") int limitPlusOne);
}
