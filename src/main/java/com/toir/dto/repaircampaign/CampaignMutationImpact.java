package com.toir.dto.repaircampaign;

import com.toir.enums.RepairCampaignStatus;

import java.util.List;

public record CampaignMutationImpact(
        boolean invalidatesApproval,
        RepairCampaignStatus currentStatus,
        long currentScopeVersion,
        long currentVersion,
        RepairCampaignStatus nextStatus,
        String reason,
        List<RepairCampaignBlocker> blockers
) {
    public CampaignMutationImpact {
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
    }
}
