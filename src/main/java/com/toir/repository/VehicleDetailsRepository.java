package com.toir.repository;

import com.toir.entity.Equipment;
import com.toir.entity.VehicleDetails;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
}
