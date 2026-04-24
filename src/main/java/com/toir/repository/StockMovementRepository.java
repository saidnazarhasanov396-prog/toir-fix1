package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.StockMovement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;


@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {
    List<StockMovement> findAllByWarehouseIdOrderByOccurredAtDesc(UUID warehouseId);
    List<StockMovement> findAllBySparePartIdOrderByOccurredAtDesc(UUID sparePartId);
    List<StockMovement> findAllByWorkOrderIdOrderByOccurredAtDesc(UUID workOrderId);
}
