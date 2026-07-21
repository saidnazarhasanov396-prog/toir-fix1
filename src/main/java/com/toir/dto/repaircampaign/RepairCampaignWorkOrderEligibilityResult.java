package com.toir.dto.repaircampaign;

import com.toir.enums.RepairCampaignWorkOrderEligibilityCode;

public record RepairCampaignWorkOrderEligibilityResult(
        boolean eligible,
        RepairCampaignWorkOrderEligibilityCode reasonCode
) {
    public static RepairCampaignWorkOrderEligibilityResult allowed() {
        return new RepairCampaignWorkOrderEligibilityResult(
                true,
                RepairCampaignWorkOrderEligibilityCode.ELIGIBLE
        );
    }

    public static RepairCampaignWorkOrderEligibilityResult ineligible(
            RepairCampaignWorkOrderEligibilityCode reasonCode
    ) {
        return new RepairCampaignWorkOrderEligibilityResult(false, reasonCode);
    }
}
