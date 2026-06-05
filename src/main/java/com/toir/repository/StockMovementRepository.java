package com.toir.repository;

import com.toir.entity.StockMovement;
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
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {
    @Query(value = "SELECT * FROM stock_movements WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<StockMovement> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM stock_movements WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<StockMovement> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = """
            SELECT
                sm.id AS id,
                sm.warehouse_id AS "warehouseId",
                wh.name AS "warehouseName",
                sm.spare_part_id AS "sparePartId",
                sp.name AS "sparePartName",
                sm.work_order_id AS "workOrderId",
                wo.number AS "workOrderNumber",
                wo.title AS "workOrderName",
                sm.type AS type,
                sm.quantity AS quantity,
                sm.unit_cost AS "unitCost",
                sm.document_number AS "documentNumber",
                sm.created_by_id AS "createdById",
                u.full_name AS "createdByFullName",
                sm.occurred_at AS "occurredAt",
                sm.notes AS notes
            FROM stock_movements sm
            LEFT JOIN warehouses wh
                ON wh.id = sm.warehouse_id
                AND wh.is_deleted = false
            LEFT JOIN spare_parts sp
                ON sp.id = sm.spare_part_id
                AND sp.is_deleted = false
            LEFT JOIN work_orders wo
                ON wo.id = sm.work_order_id
                AND wo.is_deleted = false
            LEFT JOIN users u
                ON u.id = sm.created_by_id
                AND u.is_deleted = false
            WHERE sm.is_deleted = false
              AND (
                  :scopeAdmin = true
                  OR wh.department_id = :departmentId
                  OR wh.responsible_id = :employeeId
              )
            ORDER BY sm.updated_at DESC
            """, countQuery = """
            SELECT COUNT(*)
            FROM stock_movements sm
            LEFT JOIN warehouses wh
                ON wh.id = sm.warehouse_id
                AND wh.is_deleted = false
            WHERE sm.is_deleted = false
              AND (
                  :scopeAdmin = true
                  OR wh.department_id = :departmentId
                  OR wh.responsible_id = :employeeId
              )
            """, nativeQuery = true)
    Page<StockMovementListRow> findListRows(
            @Param("scopeAdmin") boolean scopeAdmin,
            @Param("departmentId") UUID departmentId,
            @Param("employeeId") UUID employeeId,
            Pageable pageable);

    @Query(value = "SELECT * FROM stock_movements WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<StockMovement> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM stock_movements WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM stock_movements WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM stock_movements WHERE warehouse_id = :warehouseId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<StockMovement> findAllByWarehouseIdAndIsDeletedFalseOrderByOccurredAtDesc(@Param("warehouseId") UUID warehouseId);

    @Query(value = "SELECT * FROM stock_movements WHERE spare_part_id = :sparePartId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<StockMovement> findAllBySparePartIdAndIsDeletedFalseOrderByOccurredAtDesc(@Param("sparePartId") UUID sparePartId);

    @Query(value = "SELECT * FROM stock_movements WHERE work_order_id = :workOrderId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<StockMovement> findAllByWorkOrderIdAndIsDeletedFalseOrderByOccurredAtDesc(@Param("workOrderId") UUID workOrderId);
}
