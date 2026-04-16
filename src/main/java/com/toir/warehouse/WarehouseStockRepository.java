package com.toir.warehouse;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WarehouseStockRepository extends JpaRepository<WarehouseStock, UUID> {
    Optional<WarehouseStock> findByWarehouseIdAndSparePartId(UUID warehouseId, UUID sparePartId);
    List<WarehouseStock> findAllByWarehouseId(UUID warehouseId);
    List<WarehouseStock> findAllBySparePartId(UUID sparePartId);
}
