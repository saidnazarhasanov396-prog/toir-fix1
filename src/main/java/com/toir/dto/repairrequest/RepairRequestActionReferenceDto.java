package com.toir.dto.repairrequest;

import java.util.UUID;

public record RepairRequestActionReferenceDto(
        UUID templateId,
        String templateCode,
        String templateName,
        UUID operationId,
        UUID actionId,
        UUID specialistId,
        String specialistName,
        UUID specialisationId,
        String specialisationNameRu,
        String specialisationNameEn,
        String specialisationNameUz,
        int sequence,
        String name,
        Double durationHours,
        String requiredSkill,
        String customName
) {}
