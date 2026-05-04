package com.toir.repository;

import com.toir.entity.warehouse.WarehouseStock;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface WarehouseStockRepository extends JpaRepository<WarehouseStock, UUID> {
    @Query(value = "SELECT * FROM warehouse_stocks WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<WarehouseStock> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM warehouse_stocks WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<WarehouseStock> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM warehouse_stocks WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<WarehouseStock> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM warehouse_stocks WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM warehouse_stocks WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM warehouse_stocks WHERE warehouse_id = :warehouseId AND spare_part_id = :sparePartId AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<WarehouseStock> findByWarehouseIdAndSparePartIdAndIsDeletedFalse(@Param("warehouseId") UUID warehouseId, @Param("sparePartId") UUID sparePartId);

    @Query("SELECT DISTINCT ws FROM WarehouseStock ws LEFT JOIN FETCH ws.sparePart WHERE ws.warehouseId = :warehouseId AND ws.isDeleted = false ORDER BY ws.updatedAt DESC")
    List<WarehouseStock> findAllByWarehouseIdAndIsDeletedFalse(@Param("warehouseId") UUID warehouseId);

    @Query(value = "SELECT * FROM warehouse_stocks WHERE spare_part_id = :sparePartId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<WarehouseStock> findAllBySparePartIdAndIsDeletedFalse(@Param("sparePartId") UUID sparePartId);

    List<WarehouseStock> findAllBySparePartIdInAndIsDeletedFalseOrderByUpdatedAtDesc(Collection<UUID> sparePartIds);
}
