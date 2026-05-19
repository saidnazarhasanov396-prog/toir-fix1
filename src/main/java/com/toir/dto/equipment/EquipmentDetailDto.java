package com.toir.dto.equipment;

import com.toir.enums.DefectStatus;
import com.toir.enums.DowntimeType;
import com.toir.enums.RequestStatus;
import com.toir.enums.WorkOrderStatus;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record EquipmentDetailDto(
        EquipmentDto equipment,
        List<RepairRequestShortDto> repairRequests,
        List<DefectShortDto> defects,
        List<WorkOrderShortDto> workOrders,
        List<DowntimeEventShortDto> downtimeEvents
) {
    public EquipmentDetailDto {
        equipment = Objects.requireNonNull(equipment, "equipment is required");
        repairRequests = repairRequests == null ? List.of() : List.copyOf(repairRequests);
        defects = defects == null ? List.of() : List.copyOf(defects);
        workOrders = workOrders == null ? List.of() : List.copyOf(workOrders);
        downtimeEvents = downtimeEvents == null ? List.of() : List.copyOf(downtimeEvents);
    }

    public record RepairRequestShortDto(
            UUID id,
            String number,
            String title,
            RequestStatus status,
            Instant detectedAt,
            String description
    ) {
    }

    public record DefectShortDto(
            UUID id,
            String code,
            String title,
            DefectStatus status,
            Instant detectedAt,
            String description
    ) {
    }

    public record WorkOrderShortDto(
            UUID id,
            String number,
            String title,
            WorkOrderStatus status,
            Instant startedAt,
            Instant completedAt,
            String summary
    ) {
    }

    public record DowntimeEventShortDto(
            UUID id,
            Instant startAt,
            Instant endAt,
            Integer durationMinutes,
            DowntimeType type,
            String description
    ) {
    }
}
