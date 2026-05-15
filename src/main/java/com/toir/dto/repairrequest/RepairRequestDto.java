package com.toir.dto.repairrequest;

import com.toir.enums.CriticalityLevel;
import com.toir.enums.PriorityLevel;
import com.toir.enums.RequestSource;
import com.toir.enums.RequestStatus;
import com.toir.dto.triad.DefectBriefDto;
import com.toir.dto.triad.WorkOrderBriefDto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RepairRequestDto(
        UUID id,
        String number,
        String title,
        String description,
        String equipmentName,
        String departmentName,
        String locationName,
        String reporterName,
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
        String clarificationReason,
        String closeResult,
        List<DefectBriefDto> linkedDefects,
        List<WorkOrderBriefDto> linkedWorkOrders
) {
    public RepairRequestDto {
        linkedDefects = linkedDefects == null ? List.of() : List.copyOf(linkedDefects);
        linkedWorkOrders = linkedWorkOrders == null ? List.of() : List.copyOf(linkedWorkOrders);
    }
}

