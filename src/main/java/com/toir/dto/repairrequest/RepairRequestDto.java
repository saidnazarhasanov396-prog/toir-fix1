package com.toir.dto.repairrequest;

import com.toir.entity.CriticalityLevel;
import com.toir.entity.PriorityLevel;
import com.toir.entity.RepairRequest;
import com.toir.entity.RequestSource;
import com.toir.entity.RequestStatus;

import java.time.Instant;
import java.util.UUID;

public record RepairRequestDto(
        UUID id,
        String number,
        String title,
        String description,
        UUID equipmentId,
        UUID departmentId,
        UUID locationId,
        UUID reporterId,
        UUID assignedToId,
        PriorityLevel priority,
        CriticalityLevel criticality,
        RequestStatus status,
        RequestSource source,
        Instant detectedAt,
        Instant targetCompletionAt,
        Instant actualCompletionAt,
        Instant reactedAt,
        String rejectionReason,
        String closeResult
) {
    public static RepairRequestDto from(RepairRequest r) {
        return new RepairRequestDto(
                r.getId(), r.getNumber(), r.getTitle(), r.getDescription(),
                r.getEquipmentId(), r.getDepartmentId(), r.getLocationId(), r.getReporterId(), r.getAssignedToId(),
                r.getPriority(), r.getCriticality(), r.getStatus(), r.getSource(),
                r.getDetectedAt(), r.getTargetCompletionAt(), r.getActualCompletionAt(),
                r.getReactedAt(), r.getRejectionReason(), r.getCloseResult()
        );
    }
}
