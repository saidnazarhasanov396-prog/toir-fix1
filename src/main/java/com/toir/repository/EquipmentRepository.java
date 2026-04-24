package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.Equipment;
import com.toir.enums.EquipmentStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface EquipmentRepository extends JpaRepository<Equipment, UUID> {
    @Query(value = "SELECT COUNT(*) > 0 FROM equipment WHERE code = :code AND is_deleted = false", nativeQuery = true)
    boolean existsByCode(@Param("code") String code);

    @Query(value = "SELECT COUNT(*) > 0 FROM equipment WHERE inventory_number = :inventoryNumber AND is_deleted = false", nativeQuery = true)
    boolean existsByInventoryNumber(@Param("inventoryNumber") String inventoryNumber);

    @Query(value = "SELECT * FROM equipment WHERE code = :code AND is_deleted = false", nativeQuery = true)
    Optional<Equipment> findByCode(@Param("code") String code);

    @Query(value = "SELECT * FROM equipment WHERE inventory_number = :inventoryNumber AND is_deleted = false", nativeQuery = true)
    Optional<Equipment> findByInventoryNumber(@Param("inventoryNumber") String inventoryNumber);

    @Query(value = "SELECT * FROM equipment WHERE department_id = :departmentId AND is_deleted = false", nativeQuery = true)
    List<Equipment> findAllByDepartmentId(@Param("departmentId") UUID departmentId);

    @Query(value = "SELECT * FROM equipment WHERE parent_id = :parentId AND is_deleted = false", nativeQuery = true)
    List<Equipment> findAllByParentId(@Param("parentId") UUID parentId);

    @Query(value = "SELECT * FROM equipment WHERE (:departmentId IS NULL OR department_id = :departmentId) " +
            "AND (:equipmentTypeId IS NULL OR equipment_type_id = :equipmentTypeId) " +
            "AND (:status IS NULL OR status = :status) AND is_deleted = false", nativeQuery = true)
    List<Equipment> search(@Param("departmentId") UUID departmentId,
                           @Param("equipmentTypeId") UUID equipmentTypeId,
                           @Param("status") EquipmentStatus status);
}
