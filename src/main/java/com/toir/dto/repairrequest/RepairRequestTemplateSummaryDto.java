package com.toir.dto.repairrequest;

import java.util.UUID;

public record RepairRequestTemplateSummaryDto(
        UUID templateId,
        String templateCode,
        String templateName,
        int sequence
) {}
