package com.toir.service;

import com.toir.dto.meter.MeterReadingRequest;
import com.toir.dto.vehicle.VehicleDrivingSessionResponse;
import com.toir.dto.vehicle.VehicleDrivingSessionReturnRequest;
import com.toir.dto.vehicle.VehicleDrivingSessionStartRequest;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentMeter;
import com.toir.entity.equipment.VehicleDetails;
import com.toir.entity.equipment.VehicleDrivingSession;
import com.toir.entity.users.Employee;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.MeterReadingContext;
import com.toir.enums.MeterSource;
import com.toir.enums.MeterType;
import com.toir.enums.VehicleDrivingSessionStatus;
import com.toir.exception.RestException;
import com.toir.repository.VehicleDetailsRepository;
import com.toir.repository.VehicleDrivingSessionRepository;
import com.toir.repository.equipment.EquipmentMeterRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.repository.users.EmployeeWorkRoleAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VehicleDrivingSessionService {

    private static final Set<EquipmentStatus> START_ALLOWED_STATUSES = Set.of(
            EquipmentStatus.ACTIVE,
            EquipmentStatus.STANDBY
    );

    private final EquipmentRepository equipmentRepository;
    private final VehicleDetailsRepository vehicleDetailsRepository;
    private final VehicleDrivingSessionRepository sessionRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeWorkRoleAssignmentRepository employeeWorkRoleAssignmentRepository;
    private final EquipmentMeterRepository equipmentMeterRepository;
    private final MeterService meterService;

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
        if (sessionRepository.existsByEquipmentIdAndStatusAndIsDeletedFalse(equipmentId, VehicleDrivingSessionStatus.OPEN)) {
            throw RestException.conflict("Vehicle already has an open driving session");
        }
        if (sessionRepository.existsByDriverEmployeeIdAndStatusAndIsDeletedFalse(requestedDriverId, VehicleDrivingSessionStatus.OPEN)) {
            throw RestException.conflict("Driver already has an open driving session");
        }

        Instant startedAt = request != null && request.startedAt() != null ? request.startedAt() : Instant.now();
        Double startOdometer = request != null && request.startOdometerKm() != null
                ? request.startOdometerKm()
                : details.getCurrentOdometerKm();
        Double startEngineHours = request != null && request.startEngineHours() != null
                ? request.startEngineHours()
                : details.getCurrentEngineHours();

        VehicleDrivingSession session = new VehicleDrivingSession();
        session.setEquipmentId(equipmentId);
        session.setDriverEmployeeId(requestedDriverId);
        session.setStartedAt(startedAt);
        session.setStartOdometerKm(startOdometer);
        session.setStartEngineHours(startEngineHours);
        session.setIssuedBy(issuedBy);
        session.setNote(request == null ? null : trimToNull(request.note()));
        session.setStatus(VehicleDrivingSessionStatus.OPEN);

        return VehicleDrivingSessionResponse.from(sessionRepository.save(session), driver);
    }

    @Transactional
    public VehicleDrivingSessionResponse returnVehicle(UUID equipmentId,
                                                       UUID sessionId,
                                                       VehicleDrivingSessionReturnRequest request,
                                                       UUID returnedBy) {
        vehicleEquipmentOrThrow(equipmentId);
        VehicleDetails details = vehicleDetailsOrThrow(equipmentId);
        VehicleDrivingSession session = sessionRepository.findByIdAndEquipmentIdAndIsDeletedFalse(sessionId, equipmentId)
                .orElseThrow(() -> RestException.notFound("Driving session not found: " + sessionId));
        if (session.getStatus() != VehicleDrivingSessionStatus.OPEN || session.getReturnedAt() != null) {
            throw RestException.conflict("Driving session is already returned");
        }

        Instant returnedAt = request != null && request.returnedAt() != null ? request.returnedAt() : Instant.now();
        if (returnedAt.isBefore(session.getStartedAt())) {
            throw RestException.badRequest("Return time cannot be before start time");
        }
        Double endOdometer = request == null ? null : request.endOdometerKm();
        Double endEngineHours = request == null ? null : request.endEngineHours();
        validateEndReading("End odometer", session.getStartOdometerKm(), endOdometer);
        validateEndReading("End engine hours", session.getStartEngineHours(), endEngineHours);

        session.setReturnedAt(returnedAt);
        session.setEndOdometerKm(endOdometer);
        session.setEndEngineHours(endEngineHours);
        session.setReturnedBy(returnedBy);
        session.setStatus(VehicleDrivingSessionStatus.RETURNED);
        String returnNote = request == null ? null : trimToNull(request.note());
        if (returnNote != null) {
            session.setNote(returnNote);
        }

        syncVehicleReadings(details, returnedAt, endOdometer, endEngineHours, returnedBy, session.getNote());
        VehicleDrivingSession saved = sessionRepository.save(session);
        Employee driver = employeeRepository.findByIdAndIsDeletedFalse(saved.getDriverEmployeeId()).orElse(null);
        return VehicleDrivingSessionResponse.from(saved, driver);
    }

    @Transactional(readOnly = true)
    public Page<VehicleDrivingSessionResponse> history(UUID equipmentId, Pageable pageable) {
        vehicleEquipmentOrThrow(equipmentId);
        return sessionRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByStartedAtDesc(equipmentId, pageable)
                .map(session -> VehicleDrivingSessionResponse.from(
                        session,
                        employeeRepository.findByIdAndIsDeletedFalse(session.getDriverEmployeeId()).orElse(null)
                ));
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
        if (!employeeWorkRoleAssignmentRepository.existsActiveByEmployeeIdAndWorkRoleCode(driverEmployeeId, "DRIVER")) {
            throw RestException.badRequest("Employee must have DRIVER work role");
        }
        if (vehicleDepartmentId == null || !Objects.equals(driver.getDepartmentId(), vehicleDepartmentId)) {
            throw RestException.badRequest("Driver must be in the same department as the vehicle");
        }
        return driver;
    }

    private void validateEndReading(String label, Double start, Double end) {
        if (start != null && end != null && end < start) {
            throw RestException.badRequest(label + " cannot be less than the start value");
        }
    }

    private void syncVehicleReadings(VehicleDetails details,
                                     Instant returnedAt,
                                     Double endOdometer,
                                     Double endEngineHours,
                                     UUID returnedBy,
                                     String note) {
        if (endOdometer == null && endEngineHours == null) {
            return;
        }
        Map<MeterType, EquipmentMeter> metersByType = equipmentMeterRepository
                .findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(details.getEquipmentId())
                .stream()
                .collect(Collectors.toMap(
                        EquipmentMeter::getMeterType,
                        Function.identity(),
                        (a, b) -> a
                ));
        if (endOdometer != null) {
            syncMeter(metersByType.get(MeterType.MILEAGE_KM), endOdometer, returnedAt, returnedBy, note);
            details.setCurrentOdometerKm(endOdometer);
        }
        if (endEngineHours != null) {
            syncMeter(metersByType.get(MeterType.ENGINE_HOURS), endEngineHours, returnedAt, returnedBy, note);
            details.setCurrentEngineHours(endEngineHours);
        }
        vehicleDetailsRepository.save(details);
    }

    private void syncMeter(EquipmentMeter meter, Double value, Instant returnedAt, UUID returnedBy, String note) {
        if (meter == null || value == null) {
            return;
        }
        meterService.addReading(
                new MeterReadingRequest(
                        meter.getId(),
                        value,
                        returnedAt,
                        MeterSource.MANUAL,
                        returnedBy,
                        null,
                        note
                ),
                MeterReadingContext.WORK_COMPLETED,
                null,
                null,
                null
        );
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
