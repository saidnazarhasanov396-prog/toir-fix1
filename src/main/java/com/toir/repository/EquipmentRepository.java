package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.Equipment;
import com.toir.enums.EquipmentStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query("select e from Equipment e where " +
            "(:departmentId is null or e.departmentId = :departmentId) and " +
            "(:equipmentTypeId is null or e.equipmentTypeId = :equipmentTypeId) and " +
            "(:status is null or e.status = :status) and " +
            "(:search is null or " +
            "lower(e.code) like lower(concat('%', :search, '%')) or " +
            "lower(e.name) like lower(concat('%', :search, '%')) or " +
            "lower(e.inventoryNumber) like lower(concat('%', :search, '%')) or " +
            "lower(e.technicalNumber) like lower(concat('%', :search, '%')) or " +
            "lower(e.serialNumber) like lower(concat('%', :search, '%')) or " +
            "lower(e.model) like lower(concat('%', :search, '%')) or " +
            "lower(e.manufacturer) like lower(concat('%', :search, '%')) or " +
            "lower(e.description) like lower(concat('%', :search, '%')))")
    Page<Equipment> search(@Param("departmentId") UUID departmentId,
                           @Param("equipmentTypeId") UUID equipmentTypeId,
                           @Param("status") EquipmentStatus status,
                           @Param("search") String search,
                           Pageable pageable);
}
