package com.toir.dto.defect;

import com.toir.dto.triad.RepairRequestBriefDto;
import com.toir.dto.triad.WorkOrderBriefDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.toir.enums.DefectStatus;

public record DefectResponse(
        UUID id,
        String code,
        String title,
        String description,
        UUID equipmentId,
        String equipmentName,
        UUID repairRequestId,
        UUID requestId,
        String category,
        String severity,
        String failureReason,
        String rootCause,
        DefectStatus status,
        Instant detectedAt,
        Instant resolvedAt,
        int recurrenceCount,
        RepairRequestBriefDto repairRequest,
        List<WorkOrderBriefDto> linkedWorkOrders
) {
    public DefectResponse {
        linkedWorkOrders = linkedWorkOrders == null ? List.of() : List.copyOf(linkedWorkOrders);
    }

    public static DefectResponse from(DefectDto dto,
                                      String equipmentName,
                                      RepairRequestBriefDto repairRequest,
                                      List<WorkOrderBriefDto> linkedWorkOrders) {
        return new DefectResponse(
                dto.id(),
                dto.code(),
                dto.title(),
                dto.description(),
                dto.equipmentId(),
                equipmentName,
                dto.repairRequestId(),
                dto.repairRequestId(),
                dto.category(),
                dto.severity(),
                dto.failureReason(),
                dto.rootCause(),
                dto.status(),
                dto.detectedAt(),
                dto.resolvedAt(),
                dto.recurrenceCount(),
                repairRequest,
                linkedWorkOrders
        );
    }
}
