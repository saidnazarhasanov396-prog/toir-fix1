package com.toir.dto.repaircampaign;

import java.util.UUID;

public record RepairCampaignBlocker(String code, String message, String entityType, UUID entityId) {
}
