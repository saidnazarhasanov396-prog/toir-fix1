package com.toir.repository;

import com.toir.entity.warehouse.WarehouseStockBalance;
import com.toir.enums.WarehouseStockStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public interface WarehouseStockBalanceRepository extends JpaRepository<WarehouseStockBalance, UUID> {

    Optional<WarehouseStockBalance> findByIdentityKeyAndIsDeletedFalse(String identityKey);

    Page<WarehouseStockBalance> findAllByWarehouseIdAndIsDeletedFalseOrderByUpdatedAtDesc(UUID warehouseId,
                                                                                          Pageable pageable);

    @Query("""
            select b
            from WarehouseStockBalance b
            where b.warehouseId = :warehouseId
              and b.isDeleted = false
              and (:binId is null or b.binId = :binId)
              and (:sparePartId is null or b.sparePartId = :sparePartId)
              and (:stockStatus is null or b.stockStatus = :stockStatus)
              and (:lotNumber is null or lower(coalesce(b.lotNumber, '')) = lower(:lotNumber))
              and (:serialNumber is null or lower(coalesce(b.serialNumber, '')) = lower(:serialNumber))
            order by b.updatedAt desc
            """)
    Page<WarehouseStockBalance> search(@Param("warehouseId") UUID warehouseId,
                                        @Param("binId") UUID binId,
                                        @Param("sparePartId") UUID sparePartId,
                                        @Param("stockStatus") WarehouseStockStatus stockStatus,
                                        @Param("lotNumber") String lotNumber,
                                        @Param("serialNumber") String serialNumber,
                                        Pageable pageable);

    List<WarehouseStockBalance> findAllByWarehouseIdAndSparePartIdAndIsDeletedFalse(
            UUID warehouseId,
            UUID sparePartId
    );

    List<WarehouseStockBalance> findAllByIsDeletedFalse();

    List<WarehouseStockBalance> findAllByWarehouseIdAndIsDeletedFalse(UUID warehouseId);

    List<WarehouseStockBalance> findAllBySparePartIdAndIsDeletedFalse(UUID sparePartId);

    List<WarehouseStockBalance> findAllByWarehouseIdAndBinIdAndIsDeletedFalse(UUID warehouseId, UUID binId);

    boolean existsByWarehouseIdAndBinIdAndQtyOnHandGreaterThanAndIsDeletedFalse(
            UUID warehouseId,
            UUID binId,
            BigDecimal qtyOnHand
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b
            from WarehouseStockBalance b
            where b.identityKey = :identityKey
              and b.isDeleted = false
            """)
    Optional<WarehouseStockBalance> lockByIdentityKey(@Param("identityKey") String identityKey);

    @Query(value = """
            WITH legacy AS (
                SELECT warehouse_id,
                       spare_part_id,
                       'AVAILABLE'::varchar AS stock_status,
                       NULL::uuid AS legacy_bin_id,
                       SUM(quantity)::numeric(19,4) AS qty_on_hand,
                       SUM(reserved_qty)::numeric(19,4) AS qty_reserved
                FROM warehouse_stocks
                WHERE is_deleted = false
                  AND warehouse_id = :warehouseId
                GROUP BY warehouse_id, spare_part_id
            ),
            wms AS (
                SELECT warehouse_id,
                       spare_part_id,
                       stock_status,
                       SUM(qty_on_hand)::numeric(19,4) AS qty_on_hand,
                       SUM(qty_reserved)::numeric(19,4) AS qty_reserved
                FROM warehouse_stock_balances
                WHERE is_deleted = false
                  AND warehouse_id = :warehouseId
                GROUP BY warehouse_id, spare_part_id, stock_status
            ),
            stock_ledger AS (
                SELECT warehouse_id,
                       spare_part_id,
                       stock_status,
                       SUM(quantity)::numeric(19,4) AS quantity
                FROM warehouse_stock_ledgers
                WHERE is_deleted = false
                  AND warehouse_id = :warehouseId
                GROUP BY warehouse_id, spare_part_id, stock_status
            ),
            reservation_ledger AS (
                SELECT warehouse_id,
                       spare_part_id,
                       stock_status,
                       SUM(quantity)::numeric(19,4) AS quantity
                FROM warehouse_reservation_ledgers
                WHERE is_deleted = false
                  AND warehouse_id = :warehouseId
                GROUP BY warehouse_id, spare_part_id, stock_status
            ),
            stock_keys AS (
                SELECT warehouse_id, spare_part_id, stock_status FROM legacy
                UNION
                SELECT warehouse_id, spare_part_id, stock_status FROM wms
                UNION
                SELECT warehouse_id, spare_part_id, stock_status FROM stock_ledger
                UNION
                SELECT warehouse_id, spare_part_id, stock_status FROM reservation_ledger
            )
            SELECT k.warehouse_id AS "warehouseId",
                   k.spare_part_id AS "sparePartId",
                   k.stock_status AS "stockStatus",
                   l.legacy_bin_id AS "legacyBinId",
                   (l.warehouse_id IS NOT NULL AND l.legacy_bin_id IS NULL) AS "legacyBinless",
                   (l.warehouse_id IS NOT NULL) AS "legacyPresent",
                   (w.warehouse_id IS NOT NULL) AS "wmsPresent",
                   COALESCE(l.qty_on_hand, 0)::numeric(19,4) AS "legacyQtyOnHand",
                   COALESCE(l.qty_reserved, 0)::numeric(19,4) AS "legacyQtyReserved",
                   COALESCE(w.qty_on_hand, 0)::numeric(19,4) AS "wmsQtyOnHand",
                   COALESCE(w.qty_reserved, 0)::numeric(19,4) AS "wmsQtyReserved",
                   COALESCE(sl.quantity, 0)::numeric(19,4) AS "stockLedgerQty",
                   COALESCE(rl.quantity, 0)::numeric(19,4) AS "reservationLedgerQty"
            FROM stock_keys k
            LEFT JOIN legacy l
                   ON l.warehouse_id = k.warehouse_id
                  AND l.spare_part_id = k.spare_part_id
                  AND l.stock_status = k.stock_status
            LEFT JOIN wms w
                   ON w.warehouse_id = k.warehouse_id
                  AND w.spare_part_id = k.spare_part_id
                  AND w.stock_status = k.stock_status
            LEFT JOIN stock_ledger sl
                   ON sl.warehouse_id = k.warehouse_id
                  AND sl.spare_part_id = k.spare_part_id
                  AND sl.stock_status = k.stock_status
            LEFT JOIN reservation_ledger rl
                   ON rl.warehouse_id = k.warehouse_id
                  AND rl.spare_part_id = k.spare_part_id
                  AND rl.stock_status = k.stock_status
            ORDER BY k.spare_part_id, k.stock_status
            """, nativeQuery = true)
    List<WarehouseStockReconciliationRow> reconcileWarehouseStock(@Param("warehouseId") UUID warehouseId);
}
