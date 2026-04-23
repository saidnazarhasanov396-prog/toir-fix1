package com.toir.repository;
import com.toir.entity.StockMovement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {
    List<StockMovement> findAllByWarehouseIdOrderByOccurredAtDesc(UUID warehouseId);
    List<StockMovement> findAllBySparePartIdOrderByOccurredAtDesc(UUID sparePartId);
    List<StockMovement> findAllByWorkOrderIdOrderByOccurredAtDesc(UUID workOrderId);
}
