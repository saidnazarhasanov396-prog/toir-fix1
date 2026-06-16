package com.toir.dto.vehicle;

import com.toir.entity.equipment.VehicleDrivingSession;
import com.toir.entity.users.Employee;
import com.toir.enums.VehicleDrivingSessionStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record VehicleDrivingSessionResponse(
        UUID id,
        UUID equipmentId,
        UUID driverEmployeeId,
        String driverName,
        Instant startedAt,
        Instant returnedAt,
        Long durationMinutes,
        Double startOdometerKm,
        Double endOdometerKm,
        Double odometerDeltaKm,
        Double startEngineHours,
        Double endEngineHours,
        Double engineHoursDelta,
        VehicleDrivingSessionStatus status,
        UUID issuedBy,
        UUID returnedBy,
        String note
) {
    public static VehicleDrivingSessionResponse from(VehicleDrivingSession session, Employee driver) {
        return new VehicleDrivingSessionResponse(
                session.getId(),
                session.getEquipmentId(),
                session.getDriverEmployeeId(),
                driverName(driver),
                session.getStartedAt(),
                session.getReturnedAt(),
                durationMinutes(session.getStartedAt(), session.getReturnedAt()),
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

    private static String driverName(Employee driver) {
        if (driver == null) {
            return null;
        }
        return String.join(" ",
                safe(driver.getLastName()),
                safe(driver.getFirstName()),
                safe(driver.getMiddleName())
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
