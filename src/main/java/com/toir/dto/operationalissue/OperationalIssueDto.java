package com.toir.dto.operationalissue;

import com.toir.entity.Department;
import com.toir.entity.OperationalIssue;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.OperationalIssueStatus;
import com.toir.enums.OperationalIssueType;
import java.time.Instant;
import java.util.UUID;

public record OperationalIssueDto(
        UUID id,
        OperationalIssueType type,
        NotificationSeverity severity,
        OperationalIssueStatus status,
        String title,
        String message,
        UUID equipmentId,
        String equipmentName,
        UUID departmentId,
        String departmentName,
        Instant detectedAt,
        Instant resolvedAt,
        String sourceType,
        UUID sourceId
) {
    public static OperationalIssueDto from(OperationalIssue issue, Equipment equipment, Department department) {
        return new OperationalIssueDto(
                issue.getId(),
                issue.getType(),
                issue.getSeverity(),
                issue.getStatus(),
                issue.getTitle(),
                issue.getMessage(),
                issue.getEquipmentId(),
                equipment == null ? null : equipment.getName(),
                issue.getDepartmentId(),
                department == null ? null : department.getName(),
                issue.getDetectedAt(),
                issue.getResolvedAt(),
                issue.getSourceType(),
                issue.getSourceId()
        );
    }
}
