package com.toir.dto.repaircampaign;

import com.toir.enums.PriorityLevel;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RepairCampaignGenerateWorkOrdersRequest(
        UUID stageId,
        UUID departmentId,
        List<UUID> equipmentIds,
        String titleTemplate,
        Instant startPlannedAt,
        Instant endPlannedAt,
        PriorityLevel priority
) {
}
