package com.toir.equipmentsparepart;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EquipmentSparePartRepository extends JpaRepository<EquipmentSparePart, UUID> {
    List<EquipmentSparePart> findAllByEquipmentId(UUID equipmentId);
    List<EquipmentSparePart> findAllBySparePartId(UUID sparePartId);
    void deleteByEquipmentIdAndSparePartId(UUID equipmentId, UUID sparePartId);
}
