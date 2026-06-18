package com.toir.service;

import com.toir.dto.equipment.EquipmentUsageSessionResponse;
import com.toir.dto.equipment.EquipmentUsageSessionReturnRequest;
import com.toir.dto.equipment.EquipmentUsageSessionStartRequest;
import com.toir.dto.meter.MeterReadingRequest;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentMeter;
import com.toir.entity.equipment.EquipmentUsageSession;
import com.toir.entity.equipment.VehicleDetails;
import com.toir.entity.users.Employee;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.EquipmentUsageSessionStatus;
import com.toir.enums.MeterReadingContext;
import com.toir.enums.MeterSource;
import com.toir.enums.MeterType;
import com.toir.exception.RestException;
import com.toir.repository.EquipmentUsageSessionRepository;
import com.toir.repository.VehicleDetailsRepository;
import com.toir.repository.equipment.EquipmentMeterRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.users.EmployeeRepository;
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
public class EquipmentUsageSessionService {

    private static final Set<EquipmentStatus> START_ALLOWED_STATUSES = Set.of(
            EquipmentStatus.ACTIVE,
            EquipmentStatus.STANDBY
    );

    private final EquipmentRepository equipmentRepository;
    private final EquipmentUsageSessionRepository sessionRepository;
    private final EmployeeRepository employeeRepository;
    private final EquipmentMeterRepository equipmentMeterRepository;
    private final VehicleDetailsRepository vehicleDetailsRepository;
    private final MeterService meterService;

    @Transactional
    public EquipmentUsageSessionResponse start(UUID equipmentId,
                                               EquipmentUsageSessionStartRequest request,
                                               UUID issuedBy) {
        Equipment equipment = equipmentOrThrow(equipmentId);
        if (!START_ALLOWED_STATUSES.contains(equipment.getStatus())) {
            throw RestException.conflict("Equipment is not available for usage: " + equipment.getStatus());
        }
        if (request == null || request.operatorEmployeeId() == null) {
            throw RestException.badRequest("operatorEmployeeId is required");
        }
        Employee operator = validateOperator(request.operatorEmployeeId(), effectiveDepartmentId(equipment));
        if (sessionRepository.existsByEquipmentIdAndStatusAndIsDeletedFalse(equipmentId, EquipmentUsageSessionStatus.OPEN)) {
            throw RestException.conflict("Equipment already has an open usage session");
        }
        if (sessionRepository.existsByOperatorEmployeeIdAndStatusAndIsDeletedFalse(
                request.operatorEmployeeId(),
                EquipmentUsageSessionStatus.OPEN
        )) {
            throw RestException.conflict("Operator already has an open usage session");
        }

        EquipmentUsageSession session = new EquipmentUsageSession();
        session.setEquipmentId(equipmentId);
        session.setOperatorEmployeeId(request.operatorEmployeeId());
        session.setDepartmentId(effectiveDepartmentId(equipment));
        session.setStartedAt(request.startedAt() != null ? request.startedAt() : Instant.now());
        session.setMeterId(request.meterId());
        session.setStartMeterValue(request.startMeterValue());
        session.setStartOdometerKm(request.startOdometerKm());
        session.setStartEngineHours(request.startEngineHours());
        session.setIssuedBy(issuedBy);
        session.setNote(trimToNull(request.note()));
        session.setStatus(EquipmentUsageSessionStatus.OPEN);

        return EquipmentUsageSessionResponse.from(sessionRepository.save(session), operator);
    }

    @Transactional
    public EquipmentUsageSessionResponse returnEquipment(UUID equipmentId,
                                                         UUID sessionId,
                                                         EquipmentUsageSessionReturnRequest request,
                                                         UUID returnedBy) {
        Equipment equipment = equipmentOrThrow(equipmentId);
        EquipmentUsageSession session = sessionRepository.findByIdAndEquipmentIdAndIsDeletedFalse(sessionId, equipmentId)
                .orElseThrow(() -> RestException.notFound("Usage session not found: " + sessionId));
        if (session.getStatus() != EquipmentUsageSessionStatus.OPEN || session.getReturnedAt() != null) {
            throw RestException.conflict("Usage session is already returned");
        }

        Instant returnedAt = request != null && request.returnedAt() != null ? request.returnedAt() : Instant.now();
        if (returnedAt.isBefore(session.getStartedAt())) {
            throw RestException.badRequest("Return time cannot be before start time");
        }
        Double endMeterValue = request == null ? null : request.endMeterValue();
        Double endOdometer = request == null ? null : request.endOdometerKm();
        Double endEngineHours = request == null ? null : request.endEngineHours();
        validateEndReading("End meter value", session.getStartMeterValue(), endMeterValue);
        validateEndReading("End odometer", session.getStartOdometerKm(), endOdometer);
        validateEndReading("End engine hours", session.getStartEngineHours(), endEngineHours);

        session.setReturnedAt(returnedAt);
        session.setEndMeterValue(endMeterValue);
        session.setEndOdometerKm(endOdometer);
        session.setEndEngineHours(endEngineHours);
        session.setReturnedBy(returnedBy);
        session.setStatus(EquipmentUsageSessionStatus.RETURNED);
        String returnNote = request == null ? null : trimToNull(request.note());
        if (returnNote != null) {
            session.setNote(returnNote);
        }

        syncReadings(equipment, session, returnedAt, returnedBy);
        EquipmentUsageSession saved = sessionRepository.save(session);
        Employee operator = employeeRepository.findByIdAndIsDeletedFalse(saved.getOperatorEmployeeId()).orElse(null);
        return EquipmentUsageSessionResponse.from(saved, operator);
    }

    @Transactional(readOnly = true)
    public Page<EquipmentUsageSessionResponse> history(UUID equipmentId, Pageable pageable) {
        equipmentOrThrow(equipmentId);
        return sessionRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByStartedAtDesc(equipmentId, pageable)
                .map(session -> EquipmentUsageSessionResponse.from(
                        session,
                        employeeRepository.findByIdAndIsDeletedFalse(session.getOperatorEmployeeId()).orElse(null)
                ));
    }

    public boolean hasOpenSession(UUID equipmentId) {
        return sessionRepository.existsByEquipmentIdAndStatusAndIsDeletedFalse(equipmentId, EquipmentUsageSessionStatus.OPEN);
    }

    private Equipment equipmentOrThrow(UUID equipmentId) {
        return equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + equipmentId));
    }

    private Employee validateOperator(UUID operatorEmployeeId, UUID equipmentDepartmentId) {
        Employee operator = employeeRepository.findByIdAndIsDeletedFalse(operatorEmployeeId)
                .orElseThrow(() -> RestException.badRequest("Operator employee not found: " + operatorEmployeeId));
        if (!operator.isActive()) {
            throw RestException.badRequest("Operator employee is not active: " + operatorEmployeeId);
        }
        if (equipmentDepartmentId != null && !Objects.equals(operator.getDepartmentId(), equipmentDepartmentId)) {
            throw RestException.badRequest("Operator must be in the same department as the equipment");
        }
        return operator;
    }

    private UUID effectiveDepartmentId(Equipment equipment) {
        return equipment.getDepartmentId() != null ? equipment.getDepartmentId() : equipment.getResponsibleDepartmentId();
    }

    private void validateEndReading(String label, Double start, Double end) {
        if (start != null && end != null && end < start) {
            throw RestException.badRequest(label + " cannot be less than the start value");
        }
    }

    private void syncReadings(Equipment equipment,
                              EquipmentUsageSession session,
                              Instant returnedAt,
                              UUID returnedBy) {
        if (session.getMeterId() != null && session.getEndMeterValue() != null) {
            EquipmentMeter meter = equipmentMeterRepository.findByIdAndIsDeletedFalse(session.getMeterId())
                    .orElseThrow(() -> RestException.notFound("Equipment meter not found: " + session.getMeterId()));
            if (!Objects.equals(meter.getEquipmentId(), equipment.getId())) {
                throw RestException.badRequest("Meter does not belong to equipment: " + session.getMeterId());
            }
            syncMeter(meter, session.getEndMeterValue(), returnedAt, returnedBy, session.getNote());
        }
        if (equipment.getCategory() == EquipmentCategory.VEHICLE) {
            syncVehicleReadings(equipment.getId(), session, returnedAt, returnedBy);
        }
    }

    private void syncVehicleReadings(UUID equipmentId,
                                     EquipmentUsageSession session,
                                     Instant returnedAt,
                                     UUID returnedBy) {
        if (session.getEndOdometerKm() == null && session.getEndEngineHours() == null) {
            return;
        }
        VehicleDetails details = vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Vehicle details not found: " + equipmentId));
        Map<MeterType, EquipmentMeter> metersByType = equipmentMeterRepository
                .findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId)
                .stream()
                .collect(Collectors.toMap(
                        EquipmentMeter::getMeterType,
                        Function.identity(),
                        (a, b) -> a
                ));
        if (session.getEndOdometerKm() != null) {
            syncMeter(metersByType.get(MeterType.MILEAGE_KM), session.getEndOdometerKm(), returnedAt, returnedBy, session.getNote());
            details.setCurrentOdometerKm(session.getEndOdometerKm());
        }
        if (session.getEndEngineHours() != null) {
            syncMeter(metersByType.get(MeterType.ENGINE_HOURS), session.getEndEngineHours(), returnedAt, returnedBy, session.getNote());
            details.setCurrentEngineHours(session.getEndEngineHours());
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
