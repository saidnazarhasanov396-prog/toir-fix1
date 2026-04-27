package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.Equipment;
import com.toir.enums.EquipmentCategory;
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
    java.util.Optional<Equipment> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<Equipment> findAllByIsDeletedFalse();

    java.util.List<Equipment> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT COUNT(*) > 0 FROM equipment WHERE code = :code AND is_deleted = false", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);

    @Query(value = "SELECT COUNT(*) > 0 FROM equipment WHERE inventory_number = :inventoryNumber AND is_deleted = false", nativeQuery = true)
    boolean existsByInventoryNumberAndIsDeletedFalse(@Param("inventoryNumber") String inventoryNumber);

    @Query(value = "SELECT * FROM equipment WHERE code = :code AND is_deleted = false", nativeQuery = true)
    Optional<Equipment> findByCodeAndIsDeletedFalse(@Param("code") String code);

    @Query(value = "SELECT * FROM equipment WHERE inventory_number = :inventoryNumber AND is_deleted = false", nativeQuery = true)
    Optional<Equipment> findByInventoryNumberAndIsDeletedFalse(@Param("inventoryNumber") String inventoryNumber);

    @Query(value = "SELECT * FROM equipment WHERE department_id = :departmentId AND is_deleted = false", nativeQuery = true)
    List<Equipment> findAllByDepartmentIdAndIsDeletedFalse(@Param("departmentId") UUID departmentId);

    @Query(value = "SELECT * FROM equipment WHERE parent_id = :parentId AND is_deleted = false", nativeQuery = true)
    List<Equipment> findAllByParentIdAndIsDeletedFalse(@Param("parentId") UUID parentId);

    @Query("select e from Equipment e where " +
            "e.isDeleted = false and " +
            "(:departmentId is null or e.departmentId = :departmentId) and " +
            "(:equipmentTypeId is null or e.equipmentTypeId = :equipmentTypeId) and " +
            "(:status is null or e.status = :status) and " +
            "(:category is null or e.category = :category) and " +
            "(cast(:search as string) is null or " +
            "lower(e.code) like lower(concat('%', cast(:search as string), '%')) or " +
            "lower(e.name) like lower(concat('%', cast(:search as string), '%')) or " +
            "lower(e.inventoryNumber) like lower(concat('%', cast(:search as string), '%')) or " +
            "lower(e.technicalNumber) like lower(concat('%', cast(:search as string), '%')) or " +
            "lower(e.serialNumber) like lower(concat('%', cast(:search as string), '%')) or " +
            "lower(e.model) like lower(concat('%', cast(:search as string), '%')) or " +
            "lower(e.manufacturer) like lower(concat('%', cast(:search as string), '%')) or " +
            "lower(e.description) like lower(concat('%', cast(:search as string), '%')))")
    Page<Equipment> search(@Param("departmentId") UUID departmentId,
                           @Param("equipmentTypeId") UUID equipmentTypeId,
                           @Param("status") EquipmentStatus status,
                           @Param("category") EquipmentCategory category,
                           @Param("search") String search,
                           Pageable pageable);
}
