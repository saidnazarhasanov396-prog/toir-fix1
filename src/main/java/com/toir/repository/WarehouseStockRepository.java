package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.WarehouseStock;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface WarehouseStockRepository extends JpaRepository<WarehouseStock, UUID> {
    @Query(value = "SELECT * FROM warehouse_stocks WHERE warehouse_id = :warehouseId AND spare_part_id = :sparePartId AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<WarehouseStock> findByWarehouseIdAndSparePartId(@Param("warehouseId") UUID warehouseId, @Param("sparePartId") UUID sparePartId);

    @Query(value = "SELECT * FROM warehouse_stocks WHERE warehouse_id = :warehouseId AND is_deleted = false", nativeQuery = true)
    List<WarehouseStock> findAllByWarehouseId(@Param("warehouseId") UUID warehouseId);

    @Query(value = "SELECT * FROM warehouse_stocks WHERE spare_part_id = :sparePartId AND is_deleted = false", nativeQuery = true)
    List<WarehouseStock> findAllBySparePartId(@Param("sparePartId") UUID sparePartId);
}
