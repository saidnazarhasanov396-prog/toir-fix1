package com.toir.repository;

import com.toir.entity.VehicleDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleDetailsRepository extends JpaRepository<VehicleDetails, UUID> {
    Optional<VehicleDetails> findByEquipmentIdAndIsDeletedFalse(UUID equipmentId);

    List<VehicleDetails> findAllByEquipmentIdInAndIsDeletedFalse(Collection<UUID> equipmentIds);

    Optional<VehicleDetails> findByPlateNumberAndIsDeletedFalse(String plateNumber);

    Optional<VehicleDetails> findByVinAndIsDeletedFalse(String vin);

    boolean existsByPlateNumberAndIsDeletedFalse(String plateNumber);

    boolean existsByVinAndIsDeletedFalse(String vin);
}
