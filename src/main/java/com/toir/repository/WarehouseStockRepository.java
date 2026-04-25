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
    java.util.Optional<WarehouseStock> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<WarehouseStock> findAllByIsDeletedFalse();

    java.util.List<WarehouseStock> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM warehouse_stocks WHERE warehouse_id = :warehouseId AND spare_part_id = :sparePartId AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<WarehouseStock> findByWarehouseIdAndSparePartIdAndIsDeletedFalse(@Param("warehouseId") UUID warehouseId, @Param("sparePartId") UUID sparePartId);

    @Query(value = "SELECT * FROM warehouse_stocks WHERE warehouse_id = :warehouseId AND is_deleted = false", nativeQuery = true)
    List<WarehouseStock> findAllByWarehouseIdAndIsDeletedFalse(@Param("warehouseId") UUID warehouseId);

    @Query(value = "SELECT * FROM warehouse_stocks WHERE spare_part_id = :sparePartId AND is_deleted = false", nativeQuery = true)
    List<WarehouseStock> findAllBySparePartIdAndIsDeletedFalse(@Param("sparePartId") UUID sparePartId);
}
