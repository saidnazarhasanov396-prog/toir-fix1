package com.toir.repository;

import com.toir.entity.StockMovement;
import com.toir.enums.StockMovementSourceType;
import com.toir.enums.StockMovementType;
import com.toir.enums.WarehouseStockStatus;
import java.time.Instant;
import java.time.LocalDate;
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
@Deprecated(forRemoval = false)
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
                sp.type AS "sparePartType",
                sm.equipment_type_id AS "equipmentTypeId",
                et.name AS "equipmentTypeName",
                sm.work_order_id AS "workOrderId",
                wo.number AS "workOrderNumber",
                wo.title AS "workOrderName",
                sm.type AS type,
                sm.quantity AS quantity,
                sm.unit AS unit,
                sm.unit_cost AS "unitCost",
                sm.unit_price AS "unitPrice",
                sm.total_amount AS "totalAmount",
                sm.document_number AS "documentNumber",
                sm.source_type AS "sourceType",
                sm.source_id AS "sourceId",
                sm.source_line_id AS "sourceLineId",
                sm.created_by_id AS "createdById",
                u.full_name AS "createdByFullName",
                sm.responsible_person_id AS "responsiblePersonId",
                responsible.full_name AS "responsiblePersonName",
                sm.taken_by_id AS "takenById",
                taken.full_name AS "takenByName",
                sm.department_id AS "departmentId",
                sm.supplier_name AS "supplierName",
                sm.movement_date AS "movementDate",
                sm.occurred_at AS "occurredAt",
                sm.notes AS notes,
                sm.comment AS comment,
                (
                    SELECT COUNT(*)
                    FROM stock_movement_files smf
                    JOIN uploaded_files uf
                        ON uf.id = smf.file_id
                       AND uf.deleted = false
                    WHERE smf.stock_movement_id = sm.id
                ) AS "fileCount"
            FROM stock_movements sm
            LEFT JOIN warehouses wh
                ON wh.id = sm.warehouse_id
                AND wh.is_deleted = false
            LEFT JOIN spare_parts sp
                ON sp.id = sm.spare_part_id
                AND sp.is_deleted = false
            LEFT JOIN equipment_types et
                ON et.id = sm.equipment_type_id
                AND et.is_deleted = false
            LEFT JOIN work_orders wo
                ON wo.id = sm.work_order_id
                AND wo.is_deleted = false
            LEFT JOIN users u
                ON u.id = sm.created_by_id
                AND u.is_deleted = false
            LEFT JOIN users responsible
                ON responsible.id = sm.responsible_person_id
                AND responsible.is_deleted = false
            LEFT JOIN users taken
                ON taken.id = sm.taken_by_id
                AND taken.is_deleted = false
            WHERE sm.is_deleted = false
              AND (
                  :scopeAdmin = true
                  OR wh.department_id = :departmentId
                  OR wh.responsible_id = :employeeId
              )
              AND (:type IS NULL OR sm.type = :type)
              AND (:sparePartId IS NULL OR sm.spare_part_id = :sparePartId)
              AND (:warehouseId IS NULL OR sm.warehouse_id = :warehouseId)
              AND (:fromDate IS NULL OR sm.movement_date >= :fromDate)
              AND (:toDate IS NULL OR sm.movement_date <= :toDate)
              AND (:responsiblePersonId IS NULL OR sm.responsible_person_id = :responsiblePersonId)
              AND (:workOrderId IS NULL OR sm.work_order_id = :workOrderId)
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
              AND (:type IS NULL OR sm.type = :type)
              AND (:sparePartId IS NULL OR sm.spare_part_id = :sparePartId)
              AND (:warehouseId IS NULL OR sm.warehouse_id = :warehouseId)
              AND (:fromDate IS NULL OR sm.movement_date >= :fromDate)
              AND (:toDate IS NULL OR sm.movement_date <= :toDate)
              AND (:responsiblePersonId IS NULL OR sm.responsible_person_id = :responsiblePersonId)
              AND (:workOrderId IS NULL OR sm.work_order_id = :workOrderId)
            """, nativeQuery = true)
    Page<StockMovementListRow> findListRows(
            @Param("scopeAdmin") boolean scopeAdmin,
            @Param("departmentId") UUID departmentId,
            @Param("employeeId") UUID employeeId,
            @Param("type") String type,
            @Param("sparePartId") UUID sparePartId,
            @Param("warehouseId") UUID warehouseId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("responsiblePersonId") UUID responsiblePersonId,
            @Param("workOrderId") UUID workOrderId,
            Pageable pageable);


    @Query("""
            select sm
            from StockMovement sm
            where sm.isDeleted = false
              and sm.type = com.toir.enums.StockMovementType.BIN_MOVE
              and (:warehouseId is null or sm.warehouseId = :warehouseId)
              and (:sparePartId is null or sm.sparePartId = :sparePartId)
              and (:binId is null or sm.fromBinId = :binId or sm.toBinId = :binId)
              and (:stockStatus is null or sm.stockStatus = :stockStatus)
            order by sm.occurredAt desc, sm.updatedAt desc
            """)
    Page<StockMovement> searchWmsStockMoves(
            @Param("warehouseId") UUID warehouseId,
            @Param("sparePartId") UUID sparePartId,
            @Param("binId") UUID binId,
            @Param("stockStatus") WarehouseStockStatus stockStatus,
            Pageable pageable);

    @Query("""
            select sm
            from StockMovement sm
            where sm.isDeleted = false
              and sm.type = com.toir.enums.StockMovementType.TRANSFER
              and sm.sourceType = com.toir.enums.StockMovementSourceType.QUALITY_STATUS_TRANSFER
              and (:warehouseId is null or sm.warehouseId = :warehouseId)
              and (:sparePartId is null or sm.sparePartId = :sparePartId)
              and (:binId is null or sm.binId = :binId)
              and (:toStatus is null or sm.stockStatus = :toStatus)
            order by sm.occurredAt desc, sm.updatedAt desc
            """)
    Page<StockMovement> searchQualityTransfers(
            @Param("warehouseId") UUID warehouseId,
            @Param("sparePartId") UUID sparePartId,
            @Param("binId") UUID binId,
            @Param("toStatus") WarehouseStockStatus toStatus,
            Pageable pageable);

    @Query("""
            select count(sm)
            from StockMovement sm
            where sm.isDeleted = false
              and sm.type = :type
              and (:warehouseId is null or sm.warehouseId = :warehouseId)
            """)
    long countByType(@Param("warehouseId") UUID warehouseId,
                     @Param("type") StockMovementType type);

    @Query("""
            select count(sm)
            from StockMovement sm
            where sm.isDeleted = false
              and sm.type = :type
              and (:sourceType is null or sm.sourceType = :sourceType)
              and (:warehouseId is null or sm.warehouseId = :warehouseId)
              and (:stockStatus is null or sm.stockStatus = :stockStatus)
            """)
    long countByTypeAndSourceAndStatus(@Param("warehouseId") UUID warehouseId,
                                       @Param("type") StockMovementType type,
                                       @Param("sourceType") StockMovementSourceType sourceType,
                                       @Param("stockStatus") WarehouseStockStatus stockStatus);

    @Query("""
            select coalesce(sum(sm.quantity), 0)
            from StockMovement sm
            where sm.isDeleted = false
              and sm.type = :type
              and (:sourceType is null or sm.sourceType = :sourceType)
              and (:warehouseId is null or sm.warehouseId = :warehouseId)
            """)
    Double sumQuantityByTypeAndSource(@Param("warehouseId") UUID warehouseId,
                                      @Param("type") StockMovementType type,
                                      @Param("sourceType") StockMovementSourceType sourceType);

    @Query("""
            select max(sm.occurredAt)
            from StockMovement sm
            where sm.isDeleted = false
              and sm.type = :type
              and (:sourceType is null or sm.sourceType = :sourceType)
              and (:warehouseId is null or sm.warehouseId = :warehouseId)
            """)
    Instant maxOccurredAtByTypeAndSource(@Param("warehouseId") UUID warehouseId,
                                         @Param("type") StockMovementType type,
                                         @Param("sourceType") StockMovementSourceType sourceType);

    @Query("""
            select count(distinct sm.sparePartId)
            from StockMovement sm
            where sm.isDeleted = false
              and sm.type = com.toir.enums.StockMovementType.BIN_MOVE
              and (:warehouseId is null or sm.warehouseId = :warehouseId)
            """)
    long countMovedSpareParts(@Param("warehouseId") UUID warehouseId);

    @Query("""
            select count(sm)
            from StockMovement sm
            where sm.isDeleted = false
              and sm.type = com.toir.enums.StockMovementType.BIN_MOVE
              and (:warehouseId is null or sm.warehouseId = :warehouseId)
              and sm.occurredAt >= :from
            """)
    long countMovesSince(@Param("warehouseId") UUID warehouseId,
                         @Param("from") Instant from);


    @Query("""
            select sm
            from StockMovement sm
            where sm.isDeleted = false
              and (:warehouseId is null or sm.warehouseId = :warehouseId)
              and (:sparePartId is null or sm.sparePartId = :sparePartId)
              and (:stockStatus is null or sm.stockStatus = :stockStatus)
            order by sm.occurredAt desc, sm.updatedAt desc
            """)
    Page<StockMovement> searchStockMoves(
            @Param("warehouseId") UUID warehouseId,
            @Param("sparePartId") UUID sparePartId,
            @Param("stockStatus") WarehouseStockStatus stockStatus,
            Pageable pageable);

    @Query("""
            select count(sm)
            from StockMovement sm
            where sm.isDeleted = false
              and (:warehouseId is null or sm.warehouseId = :warehouseId)
            """)
    long countStockMoves(@Param("warehouseId") UUID warehouseId);

    @Query("""
            select count(sm)
            from StockMovement sm
            where sm.isDeleted = false
              and (:warehouseId is null or sm.warehouseId = :warehouseId)
              and sm.occurredAt >= :from
            """)
    long countStockMovesSince(@Param("warehouseId") UUID warehouseId,
                              @Param("from") Instant from);

    @Query("""
            select count(distinct sm.sparePartId)
            from StockMovement sm
            where sm.isDeleted = false
              and sm.sparePartId is not null
              and (:warehouseId is null or sm.warehouseId = :warehouseId)
            """)
    long countMovedSparePartsAll(@Param("warehouseId") UUID warehouseId);

    @Query("""
            select coalesce(sum(sm.quantity), 0)
            from StockMovement sm
            where sm.isDeleted = false
              and (:warehouseId is null or sm.warehouseId = :warehouseId)
            """)
    Double sumStockMoveQuantity(@Param("warehouseId") UUID warehouseId);

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
