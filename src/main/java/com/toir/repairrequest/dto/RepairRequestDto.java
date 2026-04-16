package com.toir.repairrequest.dto;

import com.toir.common.enums.CriticalityLevel;
import com.toir.common.enums.PriorityLevel;
import com.toir.repairrequest.RepairRequest;
import com.toir.repairrequest.RequestSource;
import com.toir.repairrequest.RequestStatus;

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
