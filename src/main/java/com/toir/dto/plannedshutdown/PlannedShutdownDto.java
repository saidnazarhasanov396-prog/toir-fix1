package com.toir.dto.plannedshutdown;

import com.toir.entity.PlannedShutdown;
import com.toir.enums.PlanStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record PlannedShutdownDto(
        UUID id,
        @NotBlank String name,
        @NotNull UUID departmentId,
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        @NotBlank String reason,
        PlanStatus status
) {
    public static PlannedShutdownDto from(PlannedShutdown s) {
        return new PlannedShutdownDto(s.getId(), s.getName(), s.getDepartmentId(),
                s.getStartAt(), s.getEndAt(), s.getReason(), s.getStatus());
    }
}
