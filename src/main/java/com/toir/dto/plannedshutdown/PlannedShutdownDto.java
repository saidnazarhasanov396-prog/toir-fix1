package com.toir.dto.plannedshutdown;

import com.toir.entity.PlannedShutdown;
import com.toir.enums.PlannedShutdownStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PlannedShutdownDto(
        UUID id,
        String code,
        @NotBlank String name,
        String shutdownType,
        @NotNull UUID departmentId,
        UUID responsibleEmployeeId,
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        @NotBlank String reason,
        String riskLevel,
        BigDecimal riskScore,
        PlannedShutdownStatus status
) {
    public PlannedShutdownDto(
            UUID id,
            String name,
            UUID departmentId,
            Instant startAt,
            Instant endAt,
            String reason,
            PlannedShutdownStatus status
    ) {
        this(id, null, name, null, departmentId, null, startAt, endAt, reason, null, null, status);
    }

    public static PlannedShutdownDto from(PlannedShutdown s) {
        return new PlannedShutdownDto(
                s.getId(), s.getCode(), s.getName(), s.getShutdownType(), s.getDepartmentId(),
                s.getResponsibleEmployeeId(), s.getStartAt(), s.getEndAt(), s.getReason(),
                s.getRiskLevel(), s.getRiskScore(), s.getLifecycleStatus());
    }
}
