package com.toir.dto.rcm.autoplan;

import com.toir.dto.rcm.RiskReasonDto;
import com.toir.enums.PriorityLevel;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record RcmAutoPlanPreviewRow(
        UUID equipmentId,
        String equipmentCode,
        String equipmentName,
        int riskScore,
        List<RiskReasonDto> reasons,
        UUID planId,
        String planName,
        UUID regulationId,
        String regulationName,
        LocalDateTime scheduledStart,
        LocalDateTime scheduledEnd,
        LocalDateTime dueDate,
        PriorityLevel priority,
        RcmAutoPlanDecision decision,
        UUID existingTaskId,
        String existingTaskCode,
        List<String> conflictCodes,
        String sourceKey
) {
    public RcmAutoPlanPreviewRow {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        conflictCodes = conflictCodes == null ? List.of() : List.copyOf(conflictCodes);
    }
}
