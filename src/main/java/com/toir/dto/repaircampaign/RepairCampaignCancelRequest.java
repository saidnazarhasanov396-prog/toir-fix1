package com.toir.dto.repaircampaign;

import jakarta.validation.constraints.NotBlank;

public record RepairCampaignCancelRequest(
        @NotBlank String reason
) {
}
