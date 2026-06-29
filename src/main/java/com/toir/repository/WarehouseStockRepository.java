package com.toir.repository;

import com.toir.entity.warehouse.WarehouseStock;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
@Deprecated(forRemoval = false)
public interface WarehouseStockRepository extends JpaRepository<WarehouseStock, UUID> {
    @Query(value = "SELECT * FROM warehouse_stocks WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<WarehouseStock> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from WarehouseStock s where s.id = :id and s.isDeleted = false")
    Optional<WarehouseStock> findByIdAndIsDeletedFalseForUpdate(@Param("id") UUID id);

    @Query(value = "SELECT * FROM warehouse_stocks WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<WarehouseStock> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM warehouse_stocks WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<WarehouseStock> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM warehouse_stocks WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM warehouse_stocks WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM warehouse_stocks WHERE warehouse_id = :warehouseId AND spare_part_id = :sparePartId AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<WarehouseStock> findByWarehouseIdAndSparePartIdAndIsDeletedFalse(@Param("warehouseId") UUID warehouseId, @Param("sparePartId") UUID sparePartId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s
            from WarehouseStock s
            where s.warehouseId = :warehouseId
              and s.sparePartId = :sparePartId
              and s.isDeleted = false
            """)
    Optional<WarehouseStock> findByWarehouseIdAndSparePartIdAndIsDeletedFalseForUpdate(
            @Param("warehouseId") UUID warehouseId,
            @Param("sparePartId") UUID sparePartId
    );

    @Query("SELECT DISTINCT ws FROM WarehouseStock ws LEFT JOIN FETCH ws.sparePart WHERE ws.warehouseId = :warehouseId AND ws.isDeleted = false ORDER BY ws.updatedAt DESC")
    List<WarehouseStock> findAllByWarehouseIdAndIsDeletedFalse(@Param("warehouseId") UUID warehouseId);

    @Query(nativeQuery = true, value = """
            SELECT ws.* FROM warehouse_stocks ws
            LEFT JOIN spare_parts sp ON sp.id = ws.spare_part_id AND sp.is_deleted = false
            WHERE ws.warehouse_id = :warehouseId
              AND ws.is_deleted = false
              AND (
                CAST(:search AS text) IS NULL
                OR lower(sp.name) LIKE lower(concat('%', CAST(:search AS text), '%'))
                OR lower(sp.code) LIKE lower(concat('%', CAST(:search AS text), '%'))
              )
            ORDER BY ws.updated_at DESC
            """)
    List<WarehouseStock> searchByWarehouse(
            @Param("warehouseId") UUID warehouseId,
            @Param("search") String search
    );

    @Query(value = "SELECT * FROM warehouse_stocks WHERE spare_part_id = :sparePartId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<WarehouseStock> findAllBySparePartIdAndIsDeletedFalse(@Param("sparePartId") UUID sparePartId);

    List<WarehouseStock> findAllBySparePartIdInAndIsDeletedFalseOrderByUpdatedAtDesc(Collection<UUID> sparePartIds);

    List<WarehouseStock> findAllBySparePartIdInAndWarehouseIdAndIsDeletedFalseOrderByUpdatedAtDesc(Collection<UUID> sparePartIds, UUID warehouseId);

    List<WarehouseStock> findAllBySparePartIdInAndWarehouseIdInAndIsDeletedFalseOrderByUpdatedAtDesc(Collection<UUID> sparePartIds, Collection<UUID> warehouseIds);

    @Query(value = """
            WITH filtered_parts AS (
                SELECT sp.id
                  FROM spare_parts sp
                 WHERE sp.is_deleted = false
                   AND (cast(:typeId as uuid) IS NULL OR sp.type_id = cast(:typeId as uuid))
                   AND (cast(:itemType as varchar) IS NULL OR sp.kind = cast(:itemType as varchar))
                   AND (cast(:unitId as varchar) IS NULL OR EXISTS (
                       SELECT 1 FROM units_of_measurement uom
                        WHERE uom.id = cast(:unitId as uuid)
                          AND uom.is_deleted = false
                          AND (
                               upper(sp.unit) = upper(uom.code)
                            OR upper(sp.unit) = upper(uom.name)
                            OR (uom.name_en IS NOT NULL AND upper(sp.unit) = upper(uom.name_en))
                            OR (uom.name_uz IS NOT NULL AND upper(sp.unit) = upper(uom.name_uz))
                       )
                   ))
                   AND (cast(:search as varchar) IS NULL
                       OR lower(sp.code) LIKE lower(concat('%', cast(:search as varchar), '%'))
                       OR lower(sp.name) LIKE lower(concat('%', cast(:search as varchar), '%'))
                       OR lower(sp.manufacturer) LIKE lower(concat('%', cast(:search as varchar), '%'))
                       OR lower(sp.sku) LIKE lower(concat('%', cast(:search as varchar), '%'))
                       OR lower(sp.specification) LIKE lower(concat('%', cast(:search as varchar), '%')))
            )
            SELECT
                (SELECT COUNT(DISTINCT ws.spare_part_id)
                   FROM warehouse_stocks ws
                  WHERE ws.is_deleted = false
                    AND ws.spare_part_id IN (SELECT id FROM filtered_parts)
                ) AS nomenclature,
                (SELECT COUNT(r.id)
                   FROM reservations r
                   JOIN warehouse_stocks ws ON ws.id = r.warehouse_stock_id
                  WHERE r.is_deleted = false
                    AND ws.is_deleted = false
                    AND r.status = 'ACTIVE'
                    AND ws.spare_part_id IN (SELECT id FROM filtered_parts)
                ) AS activeReservations,
                (SELECT COUNT(*)
                   FROM (
                       SELECT ws.spare_part_id
                         FROM warehouse_stocks ws
                        WHERE ws.is_deleted = false
                          AND ws.spare_part_id IN (SELECT id FROM filtered_parts)
                        GROUP BY ws.warehouse_id, ws.spare_part_id,
                                 ws.quantity, ws.reserved_qty, ws.reorder_point, ws.min_qty,
                                 (SELECT sp.min_stock FROM spare_parts sp WHERE sp.id = ws.spare_part_id)
                       HAVING (CASE
                                WHEN ws.reorder_point > 0 THEN ws.reorder_point
                                WHEN ws.min_qty > 0 THEN ws.min_qty
                                WHEN (SELECT sp.min_stock FROM spare_parts sp WHERE sp.id = ws.spare_part_id) > 0
                                     THEN (SELECT sp.min_stock FROM spare_parts sp WHERE sp.id = ws.spare_part_id)
                                ELSE NULL
                               END) IS NOT NULL
                          AND (ws.quantity - ws.reserved_qty) <= (CASE
                                WHEN ws.reorder_point > 0 THEN ws.reorder_point
                                WHEN ws.min_qty > 0 THEN ws.min_qty
                                WHEN (SELECT sp.min_stock FROM spare_parts sp WHERE sp.id = ws.spare_part_id) > 0
                                     THEN (SELECT sp.min_stock FROM spare_parts sp WHERE sp.id = ws.spare_part_id)
                                ELSE NULL
                               END)
                   ) low_stock
                ) AS lowStockItems,
                (SELECT COALESCE(SUM(sm.quantity), 0)
                   FROM stock_movements sm
                  WHERE sm.is_deleted = false
                    AND sm.type = 'ISSUE'
                    AND sm.work_order_id IS NOT NULL
                    AND sm.spare_part_id IN (SELECT id FROM filtered_parts)
                ) AS issuedToWork
            """, nativeQuery = true)
    SparePartsWarehouseStatsProjection getSparePartsWarehouseStats(
            @Param("search") String search,
            @Param("typeId") UUID typeId,
            @Param("itemType") String itemType,
            @Param("unitId") UUID unitId
    );

    @Query(value = """
            WITH filtered_parts AS (
                SELECT sp.id
                  FROM spare_parts sp
                 WHERE sp.is_deleted = false
                   AND (cast(:typeId as uuid) IS NULL OR sp.type_id = cast(:typeId as uuid))
                   AND (cast(:itemType as varchar) IS NULL OR sp.kind = cast(:itemType as varchar))
                   AND (cast(:unitId as varchar) IS NULL OR EXISTS (
                       SELECT 1 FROM units_of_measurement uom
                        WHERE uom.id = cast(:unitId as uuid)
                          AND uom.is_deleted = false
                          AND (
                               upper(sp.unit) = upper(uom.code)
                            OR upper(sp.unit) = upper(uom.name)
                            OR (uom.name_en IS NOT NULL AND upper(sp.unit) = upper(uom.name_en))
                            OR (uom.name_uz IS NOT NULL AND upper(sp.unit) = upper(uom.name_uz))
                       )
                   ))
                   AND (cast(:search as varchar) IS NULL
                       OR lower(sp.code) LIKE lower(concat('%', cast(:search as varchar), '%'))
                       OR lower(sp.name) LIKE lower(concat('%', cast(:search as varchar), '%'))
                       OR lower(sp.manufacturer) LIKE lower(concat('%', cast(:search as varchar), '%'))
                       OR lower(sp.sku) LIKE lower(concat('%', cast(:search as varchar), '%'))
                       OR lower(sp.specification) LIKE lower(concat('%', cast(:search as varchar), '%')))
            )
            SELECT
                (SELECT COUNT(DISTINCT ws.spare_part_id)
                   FROM warehouse_stocks ws
                  WHERE ws.is_deleted = false
                    AND ws.warehouse_id IN (:warehouseIds)
                    AND ws.spare_part_id IN (SELECT id FROM filtered_parts)
                ) AS nomenclature,
                (SELECT COUNT(r.id)
                   FROM reservations r
                   JOIN warehouse_stocks ws ON ws.id = r.warehouse_stock_id
                  WHERE r.is_deleted = false
                    AND ws.is_deleted = false
                    AND r.status = 'ACTIVE'
                    AND ws.warehouse_id IN (:warehouseIds)
                    AND ws.spare_part_id IN (SELECT id FROM filtered_parts)
                ) AS activeReservations,
                (SELECT COUNT(*)
                   FROM (
                       SELECT ws.spare_part_id
                         FROM warehouse_stocks ws
                        WHERE ws.is_deleted = false
                          AND ws.warehouse_id IN (:warehouseIds)
                          AND ws.spare_part_id IN (SELECT id FROM filtered_parts)
                        GROUP BY ws.warehouse_id, ws.spare_part_id,
                                 ws.quantity, ws.reserved_qty, ws.reorder_point, ws.min_qty,
                                 (SELECT sp.min_stock FROM spare_parts sp WHERE sp.id = ws.spare_part_id)
                       HAVING (CASE
                                WHEN ws.reorder_point > 0 THEN ws.reorder_point
                                WHEN ws.min_qty > 0 THEN ws.min_qty
                                WHEN (SELECT sp.min_stock FROM spare_parts sp WHERE sp.id = ws.spare_part_id) > 0
                                     THEN (SELECT sp.min_stock FROM spare_parts sp WHERE sp.id = ws.spare_part_id)
                                ELSE NULL
                               END) IS NOT NULL
                          AND (ws.quantity - ws.reserved_qty) <= (CASE
                                WHEN ws.reorder_point > 0 THEN ws.reorder_point
                                WHEN ws.min_qty > 0 THEN ws.min_qty
                                WHEN (SELECT sp.min_stock FROM spare_parts sp WHERE sp.id = ws.spare_part_id) > 0
                                     THEN (SELECT sp.min_stock FROM spare_parts sp WHERE sp.id = ws.spare_part_id)
                                ELSE NULL
                               END)
                   ) low_stock
                ) AS lowStockItems,
                (SELECT COALESCE(SUM(sm.quantity), 0)
                   FROM stock_movements sm
                  WHERE sm.is_deleted = false
                    AND sm.type = 'ISSUE'
                    AND sm.work_order_id IS NOT NULL
                    AND sm.warehouse_id IN (:warehouseIds)
                    AND sm.spare_part_id IN (SELECT id FROM filtered_parts)
                ) AS issuedToWork
            """, nativeQuery = true)
    SparePartsWarehouseStatsProjection getSparePartsWarehouseStatsByWarehouseIds(
            @Param("warehouseIds") Collection<UUID> warehouseIds,
            @Param("search") String search,
            @Param("typeId") UUID typeId,
            @Param("itemType") String itemType,
            @Param("unitId") UUID unitId
    );
}
