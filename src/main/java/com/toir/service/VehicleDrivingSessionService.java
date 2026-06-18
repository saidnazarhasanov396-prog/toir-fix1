package com.toir.service;

import com.toir.dto.equipment.EquipmentUsageSessionReturnRequest;
import com.toir.dto.equipment.EquipmentUsageSessionStartRequest;
import com.toir.dto.vehicle.VehicleDrivingSessionResponse;
import com.toir.dto.vehicle.VehicleDrivingSessionReturnRequest;
import com.toir.dto.vehicle.VehicleDrivingSessionStartRequest;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.VehicleDetails;
import com.toir.entity.users.Employee;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.exception.RestException;
import com.toir.repository.VehicleDetailsRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.users.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VehicleDrivingSessionService {

    private static final Set<EquipmentStatus> START_ALLOWED_STATUSES = Set.of(
            EquipmentStatus.ACTIVE,
            EquipmentStatus.STANDBY
    );

    private final EquipmentRepository equipmentRepository;
    private final VehicleDetailsRepository vehicleDetailsRepository;
    private final EmployeeRepository employeeRepository;
    private final EquipmentUsageSessionService equipmentUsageSessionService;

    @Transactional
    public VehicleDrivingSessionResponse start(UUID equipmentId,
                                               VehicleDrivingSessionStartRequest request,
                                               UUID issuedBy) {
        Equipment equipment = vehicleEquipmentOrThrow(equipmentId);
        if (!START_ALLOWED_STATUSES.contains(equipment.getStatus())) {
            throw RestException.conflict("Vehicle is not available for driving: " + equipment.getStatus());
        }
        VehicleDetails details = vehicleDetailsOrThrow(equipmentId);
        UUID assignedDriverId = details.getAssignedDriverId();
        if (assignedDriverId == null) {
            throw RestException.badRequest("Vehicle has no assigned driver");
        }
        UUID requestedDriverId = request != null && request.driverEmployeeId() != null
                ? request.driverEmployeeId()
                : assignedDriverId;
        Employee driver = validateDriver(requestedDriverId, equipment.getDepartmentId());
        if (!Objects.equals(assignedDriverId, requestedDriverId)) {
            throw RestException.badRequest("Driving session can only start with the assigned driver");
        }
        Instant startedAt = request != null && request.startedAt() != null ? request.startedAt() : Instant.now();
        Double startOdometer = request != null && request.startOdometerKm() != null
                ? request.startOdometerKm()
                : details.getCurrentOdometerKm();
        Double startEngineHours = request != null && request.startEngineHours() != null
                ? request.startEngineHours()
                : details.getCurrentEngineHours();

        return VehicleDrivingSessionResponse.from(equipmentUsageSessionService.start(
                equipmentId,
                new EquipmentUsageSessionStartRequest(
                        requestedDriverId,
                        startedAt,
                        null,
                        null,
                        startOdometer,
                        startEngineHours,
                        request == null ? null : trimToNull(request.note())
                ),
                issuedBy
        ));
    }

    @Transactional
    public VehicleDrivingSessionResponse returnVehicle(UUID equipmentId,
                                                       UUID sessionId,
                                                       VehicleDrivingSessionReturnRequest request,
                                                       UUID returnedBy) {
        vehicleEquipmentOrThrow(equipmentId);
        return VehicleDrivingSessionResponse.from(equipmentUsageSessionService.returnEquipment(
                equipmentId,
                sessionId,
                new EquipmentUsageSessionReturnRequest(
                        request == null ? null : request.returnedAt(),
                        null,
                        request == null ? null : request.endOdometerKm(),
                        request == null ? null : request.endEngineHours(),
                        request == null ? null : trimToNull(request.note())
                ),
                returnedBy
        ));
    }

    @Transactional(readOnly = true)
    public Page<VehicleDrivingSessionResponse> history(UUID equipmentId, Pageable pageable) {
        vehicleEquipmentOrThrow(equipmentId);
        return equipmentUsageSessionService.history(equipmentId, pageable)
                .map(VehicleDrivingSessionResponse::from);
    }

    private Equipment vehicleEquipmentOrThrow(UUID equipmentId) {
        Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + equipmentId));
        if (equipment.getCategory() != EquipmentCategory.VEHICLE) {
            throw RestException.badRequest("Equipment is not a vehicle: " + equipmentId);
        }
        return equipment;
    }

    private VehicleDetails vehicleDetailsOrThrow(UUID equipmentId) {
        return vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Vehicle details not found: " + equipmentId));
    }

    private Employee validateDriver(UUID driverEmployeeId, UUID vehicleDepartmentId) {
        Employee driver = employeeRepository.findByIdAndIsDeletedFalse(driverEmployeeId)
                .orElseThrow(() -> RestException.badRequest("Driver employee not found: " + driverEmployeeId));
        if (!driver.isActive()) {
            throw RestException.badRequest("Driver employee is not active: " + driverEmployeeId);
        }
        // DRIVER work role is no longer required — any active employee in the same department may be assigned.
        if (vehicleDepartmentId == null || !Objects.equals(driver.getDepartmentId(), vehicleDepartmentId)) {
            throw RestException.badRequest("Driver must be in the same department as the vehicle");
        }
        return driver;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
