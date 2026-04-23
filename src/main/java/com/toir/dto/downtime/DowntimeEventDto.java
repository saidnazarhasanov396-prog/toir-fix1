package com.toir.dto.downtime;

import com.toir.entity.DowntimeEvent;
import com.toir.entity.DowntimeType;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record DowntimeEventDto(
        UUID id,
        @NotNull UUID equipmentId,
        @NotNull UUID departmentId,
        UUID workOrderId,
        @NotNull Instant startAt,
        Instant endAt,
        Integer durationMinutes,
        @NotNull DowntimeType type,
        String description
) {
    public static DowntimeEventDto from(DowntimeEvent d) {
        return new DowntimeEventDto(d.getId(), d.getEquipmentId(), d.getDepartmentId(), d.getWorkOrderId(),
                d.getStartAt(), d.getEndAt(), d.getDurationMinutes(), d.getType(), d.getDescription());
    }
}
