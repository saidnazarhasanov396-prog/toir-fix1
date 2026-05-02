package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.EquipmentSparePart;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface EquipmentSparePartRepository extends JpaRepository<EquipmentSparePart, UUID> {
    java.util.Optional<EquipmentSparePart> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<EquipmentSparePart> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    java.util.List<EquipmentSparePart> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM equipment_spare_parts WHERE equipment_id = :equipmentId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<EquipmentSparePart> findAllByEquipmentIdAndIsDeletedFalse(@Param("equipmentId") UUID equipmentId);

    @Query(value = "SELECT * FROM equipment_spare_parts WHERE spare_part_id = :sparePartId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<EquipmentSparePart> findAllBySparePartIdAndIsDeletedFalse(@Param("sparePartId") UUID sparePartId);

}
