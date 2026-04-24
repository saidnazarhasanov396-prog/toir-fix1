package com.toir.repository;
import com.toir.entity.Equipment;
import com.toir.enums.EquipmentStatus;

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

    @Query(nativeQuery = true, value = """
            select e.* from equipment e where
            (:departmentId is null or e.department_id = cast(:departmentId as uuid)) 
            and (:equipmentTypeId is null or e.equipment_type_id = cast(:equipmentTypeId as uuid)) 
            and (:status is null or e.status = cast(:status as varchar))
            """)
    List<Equipment> search(@Param("departmentId") UUID departmentId,
                           @Param("equipmentTypeId") UUID equipmentTypeId,
                           @Param("status") EquipmentStatus status);
}
