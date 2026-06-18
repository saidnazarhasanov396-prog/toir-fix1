package com.toir.dto.equipment;

import com.toir.entity.equipment.EquipmentUsageSession;
import com.toir.entity.users.Employee;
import com.toir.enums.EquipmentUsageSessionStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record EquipmentUsageSessionResponse(
        UUID id,
        UUID equipmentId,
        UUID operatorEmployeeId,
        String operatorName,
        UUID departmentId,
        Instant startedAt,
        Instant returnedAt,
        Long durationMinutes,
        UUID meterId,
        Double startMeterValue,
        Double endMeterValue,
        Double meterDelta,
        Double startOdometerKm,
        Double endOdometerKm,
        Double odometerDeltaKm,
        Double startEngineHours,
        Double endEngineHours,
        Double engineHoursDelta,
        EquipmentUsageSessionStatus status,
        UUID issuedBy,
        UUID returnedBy,
        String note
) {
    public static EquipmentUsageSessionResponse from(EquipmentUsageSession session, Employee operator) {
        return new EquipmentUsageSessionResponse(
                session.getId(),
                session.getEquipmentId(),
                session.getOperatorEmployeeId(),
                employeeName(operator),
                session.getDepartmentId(),
                session.getStartedAt(),
                session.getReturnedAt(),
                durationMinutes(session.getStartedAt(), session.getReturnedAt()),
                session.getMeterId(),
                session.getStartMeterValue(),
                session.getEndMeterValue(),
                delta(session.getStartMeterValue(), session.getEndMeterValue()),
                session.getStartOdometerKm(),
                session.getEndOdometerKm(),
                delta(session.getStartOdometerKm(), session.getEndOdometerKm()),
                session.getStartEngineHours(),
                session.getEndEngineHours(),
                delta(session.getStartEngineHours(), session.getEndEngineHours()),
                session.getStatus(),
                session.getIssuedBy(),
                session.getReturnedBy(),
                session.getNote()
        );
    }

    private static String employeeName(Employee employee) {
        if (employee == null) {
            return null;
        }
        return String.join(" ",
                safe(employee.getLastName()),
                safe(employee.getFirstName()),
                safe(employee.getMiddleName())
        ).trim().replaceAll("\\s+", " ");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static Long durationMinutes(Instant startedAt, Instant returnedAt) {
        if (startedAt == null || returnedAt == null) {
            return null;
        }
        return Duration.between(startedAt, returnedAt).toMinutes();
    }

    private static Double delta(Double start, Double end) {
        if (start == null || end == null) {
            return null;
        }
        return end - start;
    }
}
