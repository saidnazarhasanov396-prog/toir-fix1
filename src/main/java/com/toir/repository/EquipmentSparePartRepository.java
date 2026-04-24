package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.EquipmentSparePart;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface EquipmentSparePartRepository extends JpaRepository<EquipmentSparePart, UUID> {
    @Query(value = "SELECT * FROM equipment_spare_parts WHERE equipment_id = :equipmentId AND is_deleted = false", nativeQuery = true)
    List<EquipmentSparePart> findAllByEquipmentId(@Param("equipmentId") UUID equipmentId);

    @Query(value = "SELECT * FROM equipment_spare_parts WHERE spare_part_id = :sparePartId AND is_deleted = false", nativeQuery = true)
    List<EquipmentSparePart> findAllBySparePartId(@Param("sparePartId") UUID sparePartId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM equipment_spare_parts WHERE equipment_id = :equipmentId AND spare_part_id = :sparePartId", nativeQuery = true)
    void deleteByEquipmentIdAndSparePartId(@Param("equipmentId") UUID equipmentId, @Param("sparePartId") UUID sparePartId);
}
