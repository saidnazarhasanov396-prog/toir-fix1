package com.toir.dto.defect;

import com.toir.dto.triad.RepairRequestBriefDto;
import com.toir.dto.triad.WorkOrderBriefDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.toir.enums.DefectStatus;
import com.toir.enums.EquipmentNodeType;

public record DefectResponse(
        UUID id,
        String code,
        String title,
        String description,
        UUID equipmentId,
        String equipmentName,
        UUID equipmentNodeId,
        String equipmentNodeCode,
        String equipmentNodeName,
        EquipmentNodeType equipmentNodeType,
        UUID defectListId,
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
        List<WorkOrderBriefDto> linkedWorkOrders,
        boolean hasLesson
) {
    public DefectResponse(UUID id,
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
                          List<WorkOrderBriefDto> linkedWorkOrders,
                          boolean hasLesson) {
        this(id, code, title, description, equipmentId, equipmentName, null, null, null, null,
                null, repairRequestId, requestId, category, severity, failureReason, rootCause, status, detectedAt,
                resolvedAt, recurrenceCount, repairRequest, linkedWorkOrders, hasLesson);
    }

    public DefectResponse {
        linkedWorkOrders = linkedWorkOrders == null ? List.of() : List.copyOf(linkedWorkOrders);
    }

    public static DefectResponse from(DefectDto dto,
                                      String equipmentName,
                                      RepairRequestBriefDto repairRequest,
                                      List<WorkOrderBriefDto> linkedWorkOrders) {
        return from(dto, equipmentName, repairRequest, linkedWorkOrders, false);
    }

    public static DefectResponse from(DefectDto dto,
                                      String equipmentName,
                                      RepairRequestBriefDto repairRequest,
                                      List<WorkOrderBriefDto> linkedWorkOrders,
                                      boolean hasLesson) {
        return from(dto, equipmentName, null, null, null, repairRequest, linkedWorkOrders, hasLesson);
    }

    public static DefectResponse from(DefectDto dto,
                                      String equipmentName,
                                      String equipmentNodeCode,
                                      String equipmentNodeName,
                                      EquipmentNodeType equipmentNodeType,
                                      RepairRequestBriefDto repairRequest,
                                      List<WorkOrderBriefDto> linkedWorkOrders,
                                      boolean hasLesson) {
        return from(dto, equipmentName, equipmentNodeCode, equipmentNodeName, equipmentNodeType,
                null, repairRequest, linkedWorkOrders, hasLesson);
    }

    public static DefectResponse from(DefectDto dto,
                                      String equipmentName,
                                      String equipmentNodeCode,
                                      String equipmentNodeName,
                                      EquipmentNodeType equipmentNodeType,
                                      UUID defectListId,
                                      RepairRequestBriefDto repairRequest,
                                      List<WorkOrderBriefDto> linkedWorkOrders,
                                      boolean hasLesson) {
        return new DefectResponse(
                dto.id(),
                dto.code(),
                dto.title(),
                dto.description(),
                dto.equipmentId(),
                equipmentName,
                dto.equipmentNodeId(),
                equipmentNodeCode,
                equipmentNodeName,
                equipmentNodeType,
                defectListId,
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
                linkedWorkOrders,
                hasLesson
        );
    }
}
