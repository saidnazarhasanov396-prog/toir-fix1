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
    java.util.Optional<StockMovement> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<StockMovement> findAllByIsDeletedFalse();

    java.util.List<StockMovement> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM stock_movements WHERE warehouse_id = :warehouseId AND is_deleted = false ORDER BY occurred_at DESC", nativeQuery = true)
    List<StockMovement> findAllByWarehouseIdAndIsDeletedFalseOrderByOccurredAtDesc(@Param("warehouseId") UUID warehouseId);

    @Query(value = "SELECT * FROM stock_movements WHERE spare_part_id = :sparePartId AND is_deleted = false ORDER BY occurred_at DESC", nativeQuery = true)
    List<StockMovement> findAllBySparePartIdAndIsDeletedFalseOrderByOccurredAtDesc(@Param("sparePartId") UUID sparePartId);

    @Query(value = "SELECT * FROM stock_movements WHERE work_order_id = :workOrderId AND is_deleted = false ORDER BY occurred_at DESC", nativeQuery = true)
    List<StockMovement> findAllByWorkOrderIdAndIsDeletedFalseOrderByOccurredAtDesc(@Param("workOrderId") UUID workOrderId);
}
