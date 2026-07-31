package com.toir.repository.equipment;

import com.toir.entity.equipment.Equipment;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentLocationType;
import com.toir.enums.EquipmentOutsideReason;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.WarehouseEquipmentStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkType;
import com.toir.repository.projection.StatusCountProjection;
import java.util.Collection;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface EquipmentRepository extends JpaRepository<Equipment, UUID> {

    @Query(value = "SELECT * FROM equipment WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<Equipment> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select equipment from Equipment equipment where equipment.id = :id and equipment.isDeleted = false")
    Optional<Equipment> findByIdAndIsDeletedFalseForUpdate(@Param("id") UUID id);

    @Query(value = "SELECT * FROM equipment WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<Equipment> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query("select equipment from Equipment equipment where equipment.isDeleted = false order by equipment.id asc")
    List<Equipment> findAllByIsDeletedFalseOrderByIdAsc();

    @Query(value = "SELECT * FROM equipment WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<Equipment> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query("""
            select equipment.id
            from Equipment equipment
            where equipment.id in :ids
              and equipment.isDeleted = false
              and (:scopeDepartmentId is null
                    or coalesce(equipment.responsibleDepartmentId, equipment.departmentId) = :scopeDepartmentId)
            """)
    List<UUID> findActiveIdsForExport(
            @Param("ids") Collection<UUID> ids,
            @Param("scopeDepartmentId") UUID scopeDepartmentId
    );

    @Query(value = """
            SELECT id
            FROM equipment
            WHERE is_deleted = false
              AND (CAST(:scopeDepartmentId AS uuid) IS NULL
                    OR COALESCE(responsible_department_id, department_id) = CAST(:scopeDepartmentId AS uuid))
              AND (CAST(:afterId AS uuid) IS NULL OR id > CAST(:afterId AS uuid))
            ORDER BY id ASC
            LIMIT :batchSize
            """, nativeQuery = true)
    List<UUID> findActiveIdsForExportAfter(
            @Param("scopeDepartmentId") UUID scopeDepartmentId,
            @Param("afterId") UUID afterId,
            @Param("batchSize") int batchSize
    );

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

    @Query("""
            select e
            from Equipment e
            where e.isDeleted = false
              and (:equipmentTypeId is null or e.equipmentTypeId = :equipmentTypeId)
            order by e.updatedAt desc
            """)
    List<Equipment> findAllForMaintenanceRegulations(@Param("equipmentTypeId") UUID equipmentTypeId);

    @Query("""
            select e.equipmentTypeId as equipmentTypeId, count(e.id) as equipmentCount
            from Equipment e
            where e.isDeleted = false
              and e.equipmentTypeId in :equipmentTypeIds
            group by e.equipmentTypeId
            """)
    List<EquipmentTypeEquipmentCountProjection> countByEquipmentTypeIds(
            @Param("equipmentTypeIds") Collection<UUID> equipmentTypeIds
    );

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
            select e from Equipment e where
            e.isDeleted = false and
            (:scopeDepartmentId is null or coalesce(e.responsibleDepartmentId, e.departmentId) = :scopeDepartmentId) and
            (:departmentId is null or e.departmentId = :departmentId) and
            (:equipmentTypeId is null or e.equipmentTypeId = :equipmentTypeId) and
            (:status is null or e.status = :status) and
            (:category is null or e.category = :category) and
            (:locationType is null or e.currentLocationType = :locationType) and
            (:warehouseId is null or e.currentWarehouseId = :warehouseId) and
            (:outsideReason is null or e.outsideReason = :outsideReason) and
            (:overdueOnly = false or (
                e.currentLocationType = com.toir.enums.EquipmentLocationType.OUTSIDE_FACILITY
                and e.outsideExpectedReturnDate is not null
                and e.outsideExpectedReturnDate < :today
            )) and
            (:hasWarranty is null
                or (:hasWarranty = true and e.hasWarranty = true)
                or (:hasWarranty = false and (e.hasWarranty = false or e.hasWarranty is null))
            ) and
            (:searchPattern is null or
            lower(e.code) like :searchPattern or
            lower(e.name) like :searchPattern or
            lower(e.inventoryNumber) like :searchPattern or
            lower(e.technicalNumber) like :searchPattern or
            lower(e.serialNumber) like :searchPattern or
            lower(e.model) like :searchPattern or
            lower(e.manufacturer) like :searchPattern or
            lower(e.description) like :searchPattern)
            order by e.updatedAt desc
            """)
    Page<Equipment> search(@Param("scopeDepartmentId") UUID scopeDepartmentId,
                           @Param("departmentId") UUID departmentId,
                           @Param("equipmentTypeId") UUID equipmentTypeId,
                           @Param("status") EquipmentStatus status,
                           @Param("category") EquipmentCategory category,
                           @Param("locationType") EquipmentLocationType locationType,
                           @Param("warehouseId") UUID warehouseId,
                           @Param("outsideReason") EquipmentOutsideReason outsideReason,
                           @Param("overdueOnly") boolean overdueOnly,
                           @Param("today") LocalDate today,
                           @Param("hasWarranty") Boolean hasWarranty,
                           @Param("searchPattern") String searchPattern,
                           Pageable pageable);

    @Query("""
            select e from Equipment e where
            e.isDeleted = false and
            (:scopeDepartmentId is null or coalesce(e.responsibleDepartmentId, e.departmentId) = :scopeDepartmentId) and
            (:departmentId is null or e.departmentId = :departmentId) and
            (:equipmentTypeId is null or e.equipmentTypeId = :equipmentTypeId) and
            (:status is null or e.status = :status) and
            (:category is null or e.category = :category) and
            (:locationType is null or e.currentLocationType = :locationType) and
            (:warehouseId is null or e.currentWarehouseId = :warehouseId) and
            (:outsideReason is null or e.outsideReason = :outsideReason) and
            (:overdueOnly = false or (
                e.currentLocationType = com.toir.enums.EquipmentLocationType.OUTSIDE_FACILITY
                and e.outsideExpectedReturnDate is not null
                and e.outsideExpectedReturnDate < :today
            )) and
            (:mxikId is null or e.mxikId = :mxikId) and
            (:hasWarranty is null
                or (:hasWarranty = true and e.hasWarranty = true)
                or (:hasWarranty = false and (e.hasWarranty = false or e.hasWarranty is null))
            ) and
            (:searchPattern is null or
            lower(e.code) like :searchPattern or
            lower(e.name) like :searchPattern or
            lower(e.inventoryNumber) like :searchPattern or
            lower(e.technicalNumber) like :searchPattern or
            lower(e.serialNumber) like :searchPattern or
            lower(e.model) like :searchPattern or
            lower(e.manufacturer) like :searchPattern or
            lower(e.description) like :searchPattern or
            exists (
                select 1
                from Mxik m
                where m.id = e.mxikId
                  and m.isDeleted = false
                  and (
                      lower(m.kod) like :searchPattern
                      or lower(m.name) like :searchPattern
                      or lower(coalesce(m.barcode, '')) like :searchPattern
                  )
            ))
            order by e.updatedAt desc
            """)
    Page<Equipment> searchWithMxik(@Param("scopeDepartmentId") UUID scopeDepartmentId,
                                   @Param("departmentId") UUID departmentId,
                                   @Param("equipmentTypeId") UUID equipmentTypeId,
                                   @Param("status") EquipmentStatus status,
                                   @Param("category") EquipmentCategory category,
                                   @Param("locationType") EquipmentLocationType locationType,
                                   @Param("warehouseId") UUID warehouseId,
                                   @Param("outsideReason") EquipmentOutsideReason outsideReason,
                                   @Param("overdueOnly") boolean overdueOnly,
                                   @Param("today") LocalDate today,
                                   @Param("mxikId") UUID mxikId,
                                   @Param("hasWarranty") Boolean hasWarranty,
                                   @Param("searchPattern") String searchPattern,
                                   Pageable pageable);

    @Query("select e from Equipment e where e.isDeleted = false " +
            "and (:equipmentId is null or e.id = :equipmentId) " +
            "and (:searchPattern is null or " +
            "lower(e.code) like :searchPattern or " +
            "lower(e.name) like :searchPattern or " +
            "lower(e.inventoryNumber) like :searchPattern) " +
            "order by e.updatedAt desc")
    Page<Equipment> searchForPassport(@Param("equipmentId") UUID equipmentId,
                                      @Param("searchPattern") String searchPattern,
                                      Pageable pageable);

    @Query("select e from Equipment e where e.isDeleted = false " +
            "and (:equipmentId is null or e.id = :equipmentId) " +
            "and (:searchPattern is null or " +
            "lower(e.code) like :searchPattern or " +
            "lower(e.name) like :searchPattern or " +
            "lower(e.inventoryNumber) like :searchPattern) " +
            "order by e.updatedAt desc")
    List<Equipment> searchAllForPassport(@Param("equipmentId") UUID equipmentId,
                                         @Param("searchPattern") String searchPattern);

    @Query("""
            select e
            from Equipment e
            where e.isDeleted = false
              and (:departmentId is null or coalesce(e.responsibleDepartmentId, e.departmentId) = :departmentId)
              and (:equipmentTypeId is null or e.equipmentTypeId = :equipmentTypeId)
              and (:status is null or e.status = :status)
              and (:category is null or e.category = :category)
              and (:hasWarranty is null
                    or (:hasWarranty = true and e.hasWarranty = true)
                    or (:hasWarranty = false and (e.hasWarranty = false or e.hasWarranty is null))
                  )
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
              and e.currentLocationType = :locationType
              and e.currentWarehouseId = :warehouseId
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
                                                  @Param("locationType") EquipmentLocationType locationType,
                                                  @Param("replacementWorkType") WorkType replacementWorkType,
                                                  @Param("finalStatuses") Collection<WorkOrderStatus> finalStatuses,
                                                  @Param("departmentId") UUID departmentId,
                                                  @Param("equipmentTypeId") UUID equipmentTypeId,
                                                  @Param("status") EquipmentStatus status,
                                                  @Param("category") EquipmentCategory category,
                                                  @Param("hasWarranty") Boolean hasWarranty,
                                                  @Param("searchPattern") String searchPattern,
                                                  Pageable pageable);

    @Query("""
            select e
            from Equipment e
            where e.isDeleted = false
              and (:departmentId is null or coalesce(e.responsibleDepartmentId, e.departmentId) = :departmentId)
              and (:equipmentTypeId is null or e.equipmentTypeId = :equipmentTypeId)
              and (:status is null or e.status = :status)
              and (:category is null or e.category = :category)
              and (:mxikId is null or e.mxikId = :mxikId)
              and (:hasWarranty is null
                    or (:hasWarranty = true and e.hasWarranty = true)
                    or (:hasWarranty = false and (e.hasWarranty = false or e.hasWarranty is null))
                  )
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
                    or exists (
                        select 1
                        from Mxik m
                        where m.id = e.mxikId
                          and m.isDeleted = false
                          and (
                              lower(m.kod) like :searchPattern
                              or lower(m.name) like :searchPattern
                              or lower(coalesce(m.barcode, '')) like :searchPattern
                          )
                    )
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
              and e.currentLocationType = :locationType
              and e.currentWarehouseId = :warehouseId
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
    Page<Equipment> searchAvailableForReplacementWithMxik(@Param("warehouseId") UUID warehouseId,
                                                          @Param("warehouseEquipmentStatus") WarehouseEquipmentStatus warehouseEquipmentStatus,
                                                          @Param("locationType") EquipmentLocationType locationType,
                                                          @Param("replacementWorkType") WorkType replacementWorkType,
                                                          @Param("finalStatuses") Collection<WorkOrderStatus> finalStatuses,
                                                          @Param("departmentId") UUID departmentId,
                                                          @Param("equipmentTypeId") UUID equipmentTypeId,
                                                          @Param("status") EquipmentStatus status,
                                                          @Param("category") EquipmentCategory category,
                                                          @Param("mxikId") UUID mxikId,
                                                          @Param("hasWarranty") Boolean hasWarranty,
                                                          @Param("searchPattern") String searchPattern,
                                                          Pageable pageable);

    default Page<Equipment> searchAvailableForReplacement(UUID warehouseId,
                                                          WarehouseEquipmentStatus warehouseEquipmentStatus,
                                                          WorkType replacementWorkType,
                                                          Collection<WorkOrderStatus> finalStatuses,
                                                          UUID departmentId,
                                                          UUID equipmentTypeId,
                                                          EquipmentStatus status,
                                                          EquipmentCategory category,
                                                          String searchPattern,
                                                          Pageable pageable) {
        return searchAvailableForReplacement(
                warehouseId,
                warehouseEquipmentStatus,
                EquipmentLocationType.WAREHOUSE,
                replacementWorkType,
                finalStatuses,
                departmentId,
                equipmentTypeId,
                status,
                category,
                null,
                searchPattern,
                pageable
        );
    }

    @Query("""
            select e.id
            from Equipment e
            where e.isDeleted = false
              and (
                    :searchPattern is null
                    or lower(coalesce(e.code, '')) like :searchPattern
                    or lower(coalesce(e.name, '')) like :searchPattern
                    or lower(coalesce(e.inventoryNumber, '')) like :searchPattern
                    or lower(coalesce(e.technicalNumber, '')) like :searchPattern
                    or lower(coalesce(e.serialNumber, '')) like :searchPattern
                  )
            order by e.updatedAt desc
            """)
    List<UUID> findIdsByBusinessSearch(@Param("searchPattern") String searchPattern);

    @Query("""
    select
        count(e.id) as totalInRegistry,

        coalesce(sum(case when e.status = :activeStatus then 1 else 0 end), 0) as active,

        coalesce(sum(case when e.status = :inRepairStatus then 1 else 0 end), 0) as inRepair,

        coalesce(sum(case when e.status = :reservedStatus then 1 else 0 end), 0) as reserved

    from Equipment e
    where e.isDeleted = false
      and (:category is null or e.category = :category)
      and (:departmentId is null or coalesce(e.responsibleDepartmentId, e.departmentId) = :departmentId)
      and (:equipmentTypeId is null or e.equipmentTypeId = :equipmentTypeId)
      and (
          :searchPattern is null
          or lower(e.code) like :searchPattern
          or lower(e.name) like :searchPattern
          or lower(e.inventoryNumber) like :searchPattern
      )
""")
    EquipmentStatsProjection getEquipmentStats(
            @Param("searchPattern") String searchPattern,
            @Param("category") EquipmentCategory category,
            @Param("departmentId") UUID departmentId,
            @Param("equipmentTypeId") UUID equipmentTypeId,
            @Param("activeStatus") EquipmentStatus activeStatus,
            @Param("inRepairStatus") EquipmentStatus inRepairStatus,
            @Param("reservedStatus") EquipmentStatus reservedStatus
    );

    @Query(nativeQuery = true, value = """
            select e.status as status, count(e.id) as count
            from equipment e
            where e.is_deleted = false
              and (cast(:departmentId as varchar) is null
                   or coalesce(e.responsible_department_id, e.department_id) = cast(:departmentId as uuid))
            group by e.status
            """)
    List<StatusCountProjection> countByStatusForCockpit(@Param("departmentId") UUID departmentId);

}
