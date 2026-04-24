package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.StockMovement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {
    @Query(value = "SELECT * FROM stock_movements WHERE warehouse_id = :warehouseId AND is_deleted = false ORDER BY occurred_at DESC", nativeQuery = true)
    List<StockMovement> findAllByWarehouseIdOrderByOccurredAtDesc(@Param("warehouseId") UUID warehouseId);

    @Query(value = "SELECT * FROM stock_movements WHERE spare_part_id = :sparePartId AND is_deleted = false ORDER BY occurred_at DESC", nativeQuery = true)
    List<StockMovement> findAllBySparePartIdOrderByOccurredAtDesc(@Param("sparePartId") UUID sparePartId);

    @Query(value = "SELECT * FROM stock_movements WHERE work_order_id = :workOrderId AND is_deleted = false ORDER BY occurred_at DESC", nativeQuery = true)
    List<StockMovement> findAllByWorkOrderIdOrderByOccurredAtDesc(@Param("workOrderId") UUID workOrderId);
}
