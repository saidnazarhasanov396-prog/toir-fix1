package com.toir.repository.equipment;

import com.toir.entity.equipment.Equipment;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.WarehouseEquipmentStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface EquipmentRepository extends JpaRepository<Equipment, UUID> {

    @Query(value = "SELECT * FROM equipment WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<Equipment> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM equipment WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<Equipment> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM equipment WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<Equipment> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM equipment WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM equipment WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT COUNT(*) > 0 FROM equipment WHERE code = :code AND is_deleted = false", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);

    @Query(value = """
            SELECT COALESCE(MAX(CAST(SUBSTRING(code FROM LENGTH(:prefix) + 1) AS BIGINT)), 0)
            FROM equipment
            WHERE code LIKE CONCAT(:prefix, '%')
              AND SUBSTRING(code FROM LENGTH(:prefix) + 1) ~ '^[0-9]+$'
            """, nativeQuery = true)
    long maxSequenceByCodePrefix(@Param("prefix") String prefix);

    @Query(value = "SELECT COUNT(*) > 0 FROM equipment WHERE inventory_number = :inventoryNumber AND is_deleted = false", nativeQuery = true)
    boolean existsByInventoryNumberAndIsDeletedFalse(@Param("inventoryNumber") String inventoryNumber);

    @Query(value = "SELECT * FROM equipment WHERE code = :code AND is_deleted = false", nativeQuery = true)
    Optional<Equipment> findByCodeAndIsDeletedFalse(@Param("code") String code);

    @Query(value = "SELECT * FROM equipment WHERE inventory_number = :inventoryNumber AND is_deleted = false", nativeQuery = true)
    Optional<Equipment> findByInventoryNumberAndIsDeletedFalse(@Param("inventoryNumber") String inventoryNumber);

    @Query(value = "SELECT * FROM equipment WHERE department_id = :departmentId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<Equipment> findAllByDepartmentIdAndIsDeletedFalse(@Param("departmentId") UUID departmentId);

    @Query(value = "SELECT * FROM equipment WHERE parent_id = :parentId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<Equipment> findAllByParentIdAndIsDeletedFalse(@Param("parentId") UUID parentId);

    @Query(
            value = "SELECT * FROM equipment WHERE parent_id = cast(:parentId as uuid) AND is_deleted = false ORDER BY updated_at DESC",
            countQuery = "SELECT COUNT(*) FROM equipment WHERE parent_id = cast(:parentId as uuid) AND is_deleted = false",
            nativeQuery = true
    )
    Page<Equipment> findAllByParentIdAndIsDeletedFalse(@Param("parentId") UUID parentId, Pageable pageable);

    @Query("select e from Equipment e where " +
            "e.isDeleted = false and " +
            "(:departmentId is null or e.departmentId = :departmentId) and " +
            "(:equipmentTypeId is null or e.equipmentTypeId = :equipmentTypeId) and " +
            "(:status is null or e.status = :status) and " +
            "(:category is null or e.category = :category) and " +
            "(:searchPattern is null or " +
            "lower(e.code) like :searchPattern or " +
            "lower(e.name) like :searchPattern or " +
            "lower(e.inventoryNumber) like :searchPattern or " +
            "lower(e.technicalNumber) like :searchPattern or " +
            "lower(e.serialNumber) like :searchPattern or " +
            "lower(e.model) like :searchPattern or " +
            "lower(e.manufacturer) like :searchPattern or " +
            "lower(e.description) like :searchPattern) " +
            "order by e.updatedAt desc")
    Page<Equipment> search(@Param("departmentId") UUID departmentId,
                           @Param("equipmentTypeId") UUID equipmentTypeId,
                           @Param("status") EquipmentStatus status,
                           @Param("category") EquipmentCategory category,
                           @Param("searchPattern") String searchPattern,
                           Pageable pageable);

    @Query("""
            select e
            from Equipment e
            where e.isDeleted = false
              and (:departmentId is null or e.departmentId = :departmentId)
              and (:equipmentTypeId is null or e.equipmentTypeId = :equipmentTypeId)
              and (:status is null or e.status = :status)
              and (:category is null or e.category = :category)
              and (
                    :searchPattern is null
                    or lower(e.code) like :searchPattern
                    or lower(e.name) like :searchPattern
                    or lower(e.inventoryNumber) like :searchPattern
                    or lower(e.technicalNumber) like :searchPattern
                    or lower(e.serialNumber) like :searchPattern
                    or lower(e.model) like :searchPattern
                    or lower(e.manufacturer) like :searchPattern
                    or lower(e.description) like :searchPattern
                  )
              and exists (
                    select 1
                    from WarehouseEquipmentItem wei
                    where wei.warehouseId = :warehouseId
                      and wei.equipmentId = e.id
                      and wei.active = true
                      and wei.isDeleted = false
                      and wei.status = :warehouseEquipmentStatus
                  )
              and not exists (
                    select 1
                    from WorkOrder wo
                    where wo.isDeleted = false
                      and wo.workType = :replacementWorkType
                      and wo.replacementEquipmentId = e.id
                      and wo.status not in :finalStatuses
                  )
            order by e.updatedAt desc
            """)
    Page<Equipment> searchAvailableForReplacement(@Param("warehouseId") UUID warehouseId,
                                                  @Param("warehouseEquipmentStatus") WarehouseEquipmentStatus warehouseEquipmentStatus,
                                                  @Param("replacementWorkType") WorkType replacementWorkType,
                                                  @Param("finalStatuses") Collection<WorkOrderStatus> finalStatuses,
                                                  @Param("departmentId") UUID departmentId,
                                                  @Param("equipmentTypeId") UUID equipmentTypeId,
                                                  @Param("status") EquipmentStatus status,
                                                  @Param("category") EquipmentCategory category,
                                                  @Param("searchPattern") String searchPattern,
                                                  Pageable pageable);
}
