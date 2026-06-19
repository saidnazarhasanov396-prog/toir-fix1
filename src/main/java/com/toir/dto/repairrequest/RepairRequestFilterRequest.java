package com.toir.dto.repairrequest;

import com.toir.enums.CriticalityLevel;
import com.toir.enums.PriorityLevel;
import com.toir.enums.RequestSource;
import com.toir.enums.RequestStatus;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;
import java.util.UUID;

public record RepairRequestFilterRequest(
        RequestStatus status,
        UUID departmentId,
        UUID equipmentId,
        PriorityLevel priority,
        String search,
        String number,
        String title,
        String description,
        UUID templateId,
        UUID locationId,
        UUID reporterId,
        UUID assignedToId,
        CriticalityLevel criticality,
        RequestSource source,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant detectedAtFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant detectedAtTo,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant targetCompletionAtFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant targetCompletionAtTo,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant actualCompletionAtFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant actualCompletionAtTo,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant reactedAtFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant reactedAtTo,
        String rejectionReason,
        String clarificationReason,
        String closeResult,
        Boolean hasLinkedDefects,
        Boolean hasLinkedWorkOrders
) {
    public RepairRequestFilterRequest withDepartmentId(UUID departmentId) {
        return new RepairRequestFilterRequest(
                status,
                departmentId,
                equipmentId,
                priority,
                search,
                number,
                title,
                description,
                templateId,
                locationId,
                reporterId,
                assignedToId,
                criticality,
                source,
                detectedAtFrom,
                detectedAtTo,
                targetCompletionAtFrom,
                targetCompletionAtTo,
                actualCompletionAtFrom,
                actualCompletionAtTo,
                reactedAtFrom,
                reactedAtTo,
                rejectionReason,
                clarificationReason,
                closeResult,
                hasLinkedDefects,
                hasLinkedWorkOrders
        );
    }
}
