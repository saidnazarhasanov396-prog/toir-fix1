package com.toir.repository;

import com.toir.entity.warehouse.WarehouseEquipmentItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WarehouseEquipmentItemRepository extends JpaRepository<WarehouseEquipmentItem, UUID> {

    @Query(value = """
            SELECT EXISTS(
                SELECT 1
                FROM warehouse_equipment_items
                WHERE warehouse_id = cast(:warehouseId as uuid)
                  AND equipment_id = cast(:equipmentId as uuid)
                  AND active = true
                  AND is_deleted = false
            )
            """, nativeQuery = true)
    boolean existsByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(@Param("warehouseId") UUID warehouseId,
                                                                             @Param("equipmentId") UUID equipmentId);

    @Query(value = """
            SELECT *
            FROM warehouse_equipment_items
            WHERE warehouse_id = cast(:warehouseId as uuid)
              AND equipment_id = cast(:equipmentId as uuid)
              AND active = true
              AND is_deleted = false
            LIMIT 1
            """, nativeQuery = true)
    Optional<WarehouseEquipmentItem> findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(@Param("warehouseId") UUID warehouseId,
                                                                                                     @Param("equipmentId") UUID equipmentId);

}
