package com.toir.repository;

import com.toir.entity.Equipment;
import com.toir.entity.VehicleDetails;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleDetailsRepository extends JpaRepository<VehicleDetails, UUID> {
    Optional<VehicleDetails> findByEquipmentIdAndIsDeletedFalse(UUID equipmentId);

    List<VehicleDetails> findAllByEquipmentIdInAndIsDeletedFalse(Collection<UUID> equipmentIds);

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
            """)
    Page<Equipment> searchVehicleEquipment(@Param("departmentId") UUID departmentId,
                                           @Param("status") EquipmentStatus status,
                                           @Param("category") EquipmentCategory category,
                                           @Param("search") String search,
                                           Pageable pageable);

    Optional<VehicleDetails> findByPlateNumberAndIsDeletedFalse(String plateNumber);

    Optional<VehicleDetails> findByVinAndIsDeletedFalse(String vin);

    boolean existsByPlateNumberAndIsDeletedFalse(String plateNumber);

    boolean existsByVinAndIsDeletedFalse(String vin);
}
