package com.toir.repository;
import com.toir.entity.Equipment;
import com.toir.entity.EquipmentStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EquipmentRepository extends JpaRepository<Equipment, UUID> {
    boolean existsByCode(String code);
    boolean existsByInventoryNumber(String inventoryNumber);
    Optional<Equipment> findByCode(String code);
    Optional<Equipment> findByInventoryNumber(String inventoryNumber);
    List<Equipment> findAllByDepartmentId(UUID departmentId);
    List<Equipment> findAllByParentId(UUID parentId);

    @Query("select e from Equipment e where (:departmentId is null or e.departmentId = :departmentId) " +
            "and (:equipmentTypeId is null or e.equipmentTypeId = :equipmentTypeId) " +
            "and (:status is null or e.status = :status)")
    List<Equipment> search(@Param("departmentId") UUID departmentId,
                           @Param("equipmentTypeId") UUID equipmentTypeId,
                           @Param("status") EquipmentStatus status);
}
