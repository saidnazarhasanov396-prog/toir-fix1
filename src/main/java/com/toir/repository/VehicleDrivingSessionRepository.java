package com.toir.repository;

import com.toir.entity.equipment.VehicleDrivingSession;
import com.toir.enums.VehicleDrivingSessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VehicleDrivingSessionRepository extends JpaRepository<VehicleDrivingSession, UUID> {

    Optional<VehicleDrivingSession> findByIdAndEquipmentIdAndIsDeletedFalse(UUID id, UUID equipmentId);

    Page<VehicleDrivingSession> findAllByEquipmentIdAndIsDeletedFalseOrderByStartedAtDesc(UUID equipmentId, Pageable pageable);

    boolean existsByEquipmentIdAndStatusAndIsDeletedFalse(UUID equipmentId, VehicleDrivingSessionStatus status);

    boolean existsByDriverEmployeeIdAndStatusAndIsDeletedFalse(UUID driverEmployeeId, VehicleDrivingSessionStatus status);
}
