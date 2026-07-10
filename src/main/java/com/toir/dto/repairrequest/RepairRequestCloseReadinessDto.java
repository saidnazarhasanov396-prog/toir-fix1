package com.toir.dto.repairrequest;

import com.toir.enums.CloseReadinessGroupStatus;
import com.toir.enums.RequestStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RepairRequestCloseReadinessDto(
        UUID repairRequestId,
        RequestStatus status,
        boolean ready,
        Instant checkedAt,
        boolean isOverdue,
        boolean reactionOverdue,
        List<RepairRequestCloseReadinessItemDto> blockers,
        List<RepairRequestCloseReadinessItemDto> warnings,
        Map<String, CloseReadinessGroupStatus> groups
) {
    public RepairRequestCloseReadinessDto {
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        groups = groups == null ? Map.of() : Map.copyOf(groups);
    }
}
