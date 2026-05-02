package com.toir.repository;

import com.toir.entity.StockMovement;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
