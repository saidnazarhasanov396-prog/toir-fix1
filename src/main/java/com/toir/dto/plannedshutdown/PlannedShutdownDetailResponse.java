package com.toir.dto.plannedshutdown;

import com.toir.entity.PlannedShutdown;
import com.toir.enums.PlannedShutdownStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

public record PlannedShutdownDetailResponse(
        UUID id, Long version, String code, String name, String shutdownType,
        UUID departmentId, UUID responsibleEmployeeId, Instant plannedStartAt, Instant plannedEndAt,
        String reason, String objective, String notes, String riskLevel, BigDecimal riskScore,
        PlannedShutdownStatus status, Long scopeVersion, Long windowVersion, Long approvalScopeVersion, String approvalScopeHash,
        Instant approvedStartAt, Instant approvedEndAt, Instant effectiveExtensionEndAt,
        Instant actualShutdownAt, Instant actualSafeStateAt, Instant actualRepairStartAt,
        Instant actualTestingStartAt, Instant actualStartupAt, Instant actualCompletedAt,
        String rescheduleReason, String extensionReason, Long closureVersion, Instant createdAt, Instant updatedAt,
        List<PlannedShutdownAssetResponse> assets,
        List<PlannedShutdownWorkItemResponse> workItems
) {
    public static PlannedShutdownDetailResponse from(PlannedShutdown s, List<PlannedShutdownAssetResponse> assets,
            List<PlannedShutdownWorkItemResponse> workItems) {
        return new PlannedShutdownDetailResponse(s.getId(), s.getVersion(), s.getCode(), s.getName(),
                s.getShutdownType(), s.getDepartmentId(), s.getResponsibleEmployeeId(), s.getPlannedStartAt(),
                s.getPlannedEndAt(), s.getReason(), s.getObjective(), s.getNotes(), s.getRiskLevel(), s.getRiskScore(),
                s.getLifecycleStatus(), s.getScopeVersion(), s.getWindowVersion(), s.getApprovalScopeVersion(), s.getApprovalScopeHash(),
                s.getApprovedStartAt(), s.getApprovedEndAt(), s.getEffectiveExtensionEndAt(), s.getActualShutdownAt(),
                s.getActualSafeStateAt(), s.getActualRepairStartAt(), s.getActualTestingStartAt(), s.getActualStartupAt(),
                s.getActualCompletedAt(), s.getRescheduleReason(), s.getExtensionReason(), s.getClosureVersion(),
                s.getCreatedAt(), s.getUpdatedAt(), assets, workItems);
    }

    public static PlannedShutdownDetailResponse from(PlannedShutdown s, List<PlannedShutdownAssetResponse> assets) {
        return from(s, assets, List.of());
    }

    private static Long seconds(Instant start, Instant end) {
        return start == null || end == null ? null : Duration.between(start, end).getSeconds();
    }

    public Long plannedDowntimeSeconds() { return seconds(plannedStartAt, plannedEndAt); }
    public Long actualDowntimeSeconds() { return seconds(actualShutdownAt, actualCompletedAt); }
}
