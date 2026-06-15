package com.toir.dto.repairrequest;

import com.toir.enums.CriticalityLevel;
import com.toir.dto.meter.MeterReadingDto;
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
        List<WorkOrderBriefDto> linkedWorkOrders,
        List<MeterReadingDto> meterReadings
) {
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
        this(id, number, title, description, templateId, equipmentId, equipmentName, departmentId, departmentName,
                locationName, reporterId, reporterName, assignedToId, priority, criticality, status, source,
                detectedAt, targetCompletionAt, actualCompletionAt, reactedAt, rejectionReason,
                clarificationReason, closeResult, linkedDefects, linkedWorkOrders, List.of());
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
        this(id, number, title, description, null, equipmentId, equipmentName, departmentId, departmentName,
                locationName, reporterId, reporterName, assignedToId, priority, criticality, status, source,
                detectedAt, targetCompletionAt, actualCompletionAt, reactedAt, rejectionReason,
                clarificationReason, closeResult, linkedDefects, linkedWorkOrders, List.of());
    }

    public RepairRequestDto {
        linkedDefects = linkedDefects == null ? List.of() : List.copyOf(linkedDefects);
        linkedWorkOrders = linkedWorkOrders == null ? List.of() : List.copyOf(linkedWorkOrders);
        meterReadings = meterReadings == null ? List.of() : List.copyOf(meterReadings);
    }
}
