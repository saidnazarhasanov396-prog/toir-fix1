package com.toir.dto.repairrequest;

import com.toir.enums.CriticalityLevel;
import com.toir.dto.meter.MeterReadingDto;
import com.toir.enums.PriorityLevel;
import com.toir.enums.RequestSource;
import com.toir.enums.RequestStatus;
import com.toir.enums.WarrantyHandling;
import com.toir.dto.triad.DefectBriefDto;
import com.toir.dto.triad.WorkOrderBriefDto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RepairRequestDto(
        UUID id,
        String number,
        String title,
        String description,
        UUID templateId,
        List<UUID> templateIds,
        List<RepairRequestTemplateSummaryDto> templates,
        List<RepairRequestActionReferenceDto> actionReferences,
        UUID equipmentId,
        String equipmentName,
        UUID departmentId,
        String departmentName,
        String locationName,
        UUID reporterId,
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
        List<WorkOrderBriefDto> linkedWorkOrders,
        List<MeterReadingDto> meterReadings,
        Boolean warrantyActiveAtCreation,
        WarrantyHandling warrantyHandling,
        String warrantyDecisionComment,
        Instant supplierContactedAt,
        String supplierResponse,
        String emergencyReason,
        UUID warrantyCounteragentId,
        String warrantyCounteragentName,
        String warrantyCounteragentContactPerson,
        String warrantyCounteragentPhone,
        String warrantyCounteragentEmail,
        LocalDate warrantyStartDateAtCreation,
        LocalDate warrantyEndDateAtCreation
) {
    public RepairRequestDto(
            UUID id,
            String number,
            String title,
            String description,
            UUID templateId,
            List<UUID> templateIds,
            List<RepairRequestTemplateSummaryDto> templates,
            List<RepairRequestActionReferenceDto> actionReferences,
            UUID equipmentId,
            String equipmentName,
            UUID departmentId,
            String departmentName,
            String locationName,
            UUID reporterId,
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
            List<WorkOrderBriefDto> linkedWorkOrders,
            List<MeterReadingDto> meterReadings,
            Boolean warrantyActiveAtCreation,
            WarrantyHandling warrantyHandling,
            String warrantyDecisionComment,
            Instant supplierContactedAt,
            String supplierResponse,
            String emergencyReason
    ) {
        this(id, number, title, description, templateId, templateIds, templates, actionReferences,
                equipmentId, equipmentName, departmentId, departmentName, locationName, reporterId, reporterName,
                assignedToId, priority, criticality, status, source, detectedAt, targetCompletionAt,
                actualCompletionAt, reactedAt, rejectionReason, clarificationReason, closeResult,
                linkedDefects, linkedWorkOrders, meterReadings, warrantyActiveAtCreation, warrantyHandling,
                warrantyDecisionComment, supplierContactedAt, supplierResponse, emergencyReason,
                null, null, null, null, null, null, null);
    }

    public RepairRequestDto(
            UUID id,
            String number,
            String title,
            String description,
            UUID templateId,
            UUID equipmentId,
            String equipmentName,
            UUID departmentId,
            String departmentName,
            String locationName,
            UUID reporterId,
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
        this(id, number, title, description, templateId, List.of(), List.of(), List.of(), equipmentId, equipmentName, departmentId, departmentName,
                locationName, reporterId, reporterName, assignedToId, priority, criticality, status, source,
                detectedAt, targetCompletionAt, actualCompletionAt, reactedAt, rejectionReason,
                clarificationReason, closeResult, linkedDefects, linkedWorkOrders, List.of(),
                null, null, null, null, null, null,
                null, null, null, null, null, null, null);
    }

    public RepairRequestDto(
            UUID id,
            String number,
            String title,
            String description,
            UUID equipmentId,
            String equipmentName,
            UUID departmentId,
            String departmentName,
            String locationName,
            UUID reporterId,
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
        this(id, number, title, description, null, List.of(), List.of(), List.of(), equipmentId, equipmentName, departmentId, departmentName,
                locationName, reporterId, reporterName, assignedToId, priority, criticality, status, source,
                detectedAt, targetCompletionAt, actualCompletionAt, reactedAt, rejectionReason,
                clarificationReason, closeResult, linkedDefects, linkedWorkOrders, List.of(),
                null, null, null, null, null, null,
                null, null, null, null, null, null, null);
    }

    public RepairRequestDto {
        templateIds = templateIds == null ? List.of() : List.copyOf(templateIds);
        templates = templates == null ? List.of() : List.copyOf(templates);
        actionReferences = actionReferences == null ? List.of() : List.copyOf(actionReferences);
        linkedDefects = linkedDefects == null ? List.of() : List.copyOf(linkedDefects);
        linkedWorkOrders = linkedWorkOrders == null ? List.of() : List.copyOf(linkedWorkOrders);
        meterReadings = meterReadings == null ? List.of() : List.copyOf(meterReadings);
    }
}
