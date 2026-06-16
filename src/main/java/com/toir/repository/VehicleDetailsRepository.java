package com.toir.repository;

import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.VehicleDetails;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.VehicleRegistrationPlateType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.toir.repository.projection.VehicleStatsProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VehicleDetailsRepository extends JpaRepository<VehicleDetails, UUID> {
    @Query(value = "SELECT * FROM vehicle_details WHERE equipment_id = cast(:equipmentId as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<VehicleDetails> findByEquipmentIdAndIsDeletedFalse(@Param("equipmentId") UUID equipmentId);

    @Query(value = "SELECT * FROM vehicle_details WHERE equipment_id IN (:equipmentIds) AND is_deleted = false", nativeQuery = true)
    List<VehicleDetails> findAllByEquipmentIdInAndIsDeletedFalse(@Param("equipmentIds") Collection<UUID> equipmentIds);

    @Query("""
            select e from Equipment e
            join VehicleDetails vd on vd.equipmentId = e.id
            where e.isDeleted = false
            and vd.isDeleted = false
            and e.category = :category
            and (:departmentId is null or e.departmentId = :departmentId)
            and (:status is null or e.status = :status)
            and (:plateType is null or vd.plateType = :plateType)
            and (cast(:search as string) is null or :search = '' or
                lower(e.code) like lower(concat('%', cast(:search as string), '%')) or
                lower(e.name) like lower(concat('%', cast(:search as string), '%')) or
                lower(e.inventoryNumber) like lower(concat('%', cast(:search as string), '%')) or
                lower(e.technicalNumber) like lower(concat('%', cast(:search as string), '%')) or
                lower(e.serialNumber) like lower(concat('%', cast(:search as string), '%')) or
                lower(e.model) like lower(concat('%', cast(:search as string), '%')) or
                lower(e.manufacturer) like lower(concat('%', cast(:search as string), '%')) or
                lower(vd.plateNumber) like lower(concat('%', cast(:search as string), '%')) or
                lower(vd.vin) like lower(concat('%', cast(:search as string), '%')) or
                lower(vd.brand) like lower(concat('%', cast(:search as string), '%')) or
                lower(vd.model) like lower(concat('%', cast(:search as string), '%')))
            order by e.updatedAt desc
            """)
    Page<Equipment> searchVehicleEquipment(@Param("departmentId") UUID departmentId,
                                           @Param("status") EquipmentStatus status,
                                           @Param("plateType") VehicleRegistrationPlateType plateType,
                                           @Param("category") EquipmentCategory category,
                                           @Param("search") String search,
                                           Pageable pageable);

    @Query(value = "SELECT * FROM vehicle_details WHERE plate_number = cast(:plateNumber as varchar) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<VehicleDetails> findByPlateNumberAndIsDeletedFalse(@Param("plateNumber") String plateNumber);

    @Query(value = "SELECT * FROM vehicle_details WHERE vin = cast(:vin as varchar) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<VehicleDetails> findByVinAndIsDeletedFalse(@Param("vin") String vin);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM vehicle_details WHERE plate_number = cast(:plateNumber as varchar) AND is_deleted = false)", nativeQuery = true)
    boolean existsByPlateNumberAndIsDeletedFalse(@Param("plateNumber") String plateNumber);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM vehicle_details WHERE vin = cast(:vin as varchar) AND is_deleted = false)", nativeQuery = true)
    boolean existsByVinAndIsDeletedFalse(@Param("vin") String vin);

    @Query(value = """
            SELECT EXISTS(
                SELECT 1
                FROM vehicle_details
                WHERE assigned_driver_id = cast(:driverEmployeeId as uuid)
                  AND is_deleted = false
            )
            """, nativeQuery = true)
    boolean existsByAssignedDriverIdAndIsDeletedFalse(@Param("driverEmployeeId") UUID driverEmployeeId);

    @Query(value = """
            SELECT EXISTS(
                SELECT 1
                FROM vehicle_details
                WHERE assigned_driver_id = cast(:driverEmployeeId as uuid)
                  AND equipment_id <> cast(:equipmentId as uuid)
                  AND is_deleted = false
            )
            """, nativeQuery = true)
    boolean existsAssignedDriverOnAnotherVehicle(
            @Param("driverEmployeeId") UUID driverEmployeeId,
            @Param("equipmentId") UUID equipmentId
    );

    @Query("""
        select
            count(e.id) as total,

            coalesce(sum(case when e.status = :activeStatus then 1 else 0 end), 0) as active,

            coalesce(sum(case when e.status = :inRepairStatus then 1 else 0 end), 0) as inRepair,

            coalesce(sum(case when e.status = :outOfServiceStatus then 1 else 0 end), 0) as outOfService

        from Equipment e
        join VehicleDetails vd on vd.equipmentId = e.id
        where e.isDeleted = false
          and vd.isDeleted = false
          and e.category = :category
          and (:departmentId is null or e.departmentId = :departmentId)
          and (
              :searchPattern is null
              or lower(e.code) like :searchPattern
              or lower(e.name) like :searchPattern
              or lower(e.inventoryNumber) like :searchPattern
              or lower(e.technicalNumber) like :searchPattern
              or lower(e.serialNumber) like :searchPattern
              or lower(e.model) like :searchPattern
              or lower(e.manufacturer) like :searchPattern
              or lower(vd.plateNumber) like :searchPattern
              or lower(vd.vin) like :searchPattern
              or lower(vd.brand) like :searchPattern
              or lower(vd.model) like :searchPattern
          )
        """)
    VehicleStatsProjection getVehicleStats(
            @Param("departmentId") UUID departmentId,
            @Param("category") EquipmentCategory category,
            @Param("searchPattern") String searchPattern,
            @Param("activeStatus") EquipmentStatus activeStatus,
            @Param("inRepairStatus") EquipmentStatus inRepairStatus,
            @Param("outOfServiceStatus") EquipmentStatus outOfServiceStatus
    );

}
