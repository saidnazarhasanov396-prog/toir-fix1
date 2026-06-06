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

    @Query(value = "SELECT * FROM warehouse_stocks WHERE spare_part_id = :sparePartId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<WarehouseStock> findAllBySparePartIdAndIsDeletedFalse(@Param("sparePartId") UUID sparePartId);

    List<WarehouseStock> findAllBySparePartIdInAndIsDeletedFalseOrderByUpdatedAtDesc(Collection<UUID> sparePartIds);

    List<WarehouseStock> findAllBySparePartIdInAndWarehouseIdAndIsDeletedFalseOrderByUpdatedAtDesc(Collection<UUID> sparePartIds, UUID warehouseId);

    List<WarehouseStock> findAllBySparePartIdInAndWarehouseIdInAndIsDeletedFalseOrderByUpdatedAtDesc(Collection<UUID> sparePartIds, Collection<UUID> warehouseIds);

    @Query(value = """
            SELECT
                (SELECT COUNT(DISTINCT ws.spare_part_id)
                   FROM warehouse_stocks ws
                  WHERE ws.is_deleted = false) AS nomenclature,
                (SELECT COUNT(r.id)
                   FROM reservations r
                   JOIN warehouse_stocks ws ON ws.id = r.warehouse_stock_id
                  WHERE r.is_deleted = false
                    AND ws.is_deleted = false
                    AND r.status = 'ACTIVE') AS activeReservations,
                (SELECT COUNT(ws.id)
                   FROM warehouse_stocks ws
                  WHERE ws.is_deleted = false
                    AND COALESCE(ws.reorder_point, ws.min_qty) > 0
                    AND (ws.quantity - ws.reserved_qty) <= COALESCE(ws.reorder_point, ws.min_qty)) AS lowStockItems,
                (SELECT COALESCE(SUM(sm.quantity), 0)
                   FROM stock_movements sm
                  WHERE sm.is_deleted = false
                    AND sm.type = 'ISSUE'
                    AND sm.work_order_id IS NOT NULL) AS issuedToWork
            """, nativeQuery = true)
    SparePartsWarehouseStatsProjection getSparePartsWarehouseStats();

    @Query(value = """
            SELECT
                (SELECT COUNT(DISTINCT ws.spare_part_id)
                   FROM warehouse_stocks ws
                  WHERE ws.is_deleted = false
                    AND ws.warehouse_id IN (:warehouseIds)) AS nomenclature,
                (SELECT COUNT(r.id)
                   FROM reservations r
                   JOIN warehouse_stocks ws ON ws.id = r.warehouse_stock_id
                  WHERE r.is_deleted = false
                    AND ws.is_deleted = false
                    AND r.status = 'ACTIVE'
                    AND ws.warehouse_id IN (:warehouseIds)) AS activeReservations,
                (SELECT COUNT(ws.id)
                   FROM warehouse_stocks ws
                  WHERE ws.is_deleted = false
                    AND ws.warehouse_id IN (:warehouseIds)
                    AND COALESCE(ws.reorder_point, ws.min_qty) > 0
                    AND (ws.quantity - ws.reserved_qty) <= COALESCE(ws.reorder_point, ws.min_qty)) AS lowStockItems,
                (SELECT COALESCE(SUM(sm.quantity), 0)
                   FROM stock_movements sm
                  WHERE sm.is_deleted = false
                    AND sm.type = 'ISSUE'
                    AND sm.work_order_id IS NOT NULL
                    AND sm.warehouse_id IN (:warehouseIds)) AS issuedToWork
            """, nativeQuery = true)
    SparePartsWarehouseStatsProjection getSparePartsWarehouseStatsByWarehouseIds(@Param("warehouseIds") Collection<UUID> warehouseIds);
}
