package com.toir.dto.repaircampaign;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.toir.enums.RepairCampaignWorkOrderEligibilityCode;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkOrderType;

import java.time.Instant;
import java.util.UUID;

public record RepairCampaignWorkOrderCandidateDto(
        UUID id,
        String number,
        String title,
        WorkOrderType type,
        WorkOrderStatus status,
        UUID departmentId,
        Instant startPlannedAt,
        Instant endPlannedAt,
        boolean eligible,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        RepairCampaignWorkOrderEligibilityCode ineligibleReasonCode
) { }
