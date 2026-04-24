package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.WarehouseStock;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface WarehouseStockRepository extends JpaRepository<WarehouseStock, UUID> {
    Optional<WarehouseStock> findByWarehouseIdAndSparePartId(UUID warehouseId, UUID sparePartId);
    List<WarehouseStock> findAllByWarehouseId(UUID warehouseId);
    List<WarehouseStock> findAllBySparePartId(UUID sparePartId);
}
