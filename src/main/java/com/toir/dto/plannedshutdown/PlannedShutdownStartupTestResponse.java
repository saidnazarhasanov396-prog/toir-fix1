package com.toir.dto.plannedshutdown;

import com.toir.entity.plannedshutdown.PlannedShutdownStartupTest;
import com.toir.enums.PlannedShutdownItemStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PlannedShutdownStartupTestResponse(
        UUID id, UUID plannedShutdownId, String testKey, String title, boolean mandatory,
        String acceptanceCriteria, String unit, Integer orderNumber, PlannedShutdownItemStatus status,
        BigDecimal measuredValue, String resultUnit, String evidence, UUID performerId, UUID verifierId,
        Instant verifiedAt) {
    public static PlannedShutdownStartupTestResponse from(PlannedShutdownStartupTest test) {
        return new PlannedShutdownStartupTestResponse(test.getId(), test.getPlannedShutdownId(), test.getTestKey(),
                test.getTitle(), test.isMandatory(), test.getAcceptanceCriteria(), test.getUnit(), test.getOrderNumber(),
                test.getStatus(), test.getMeasuredValue(), test.getResultUnit(), test.getEvidence(),
                test.getPerformerId(), test.getVerifierId(), test.getVerifiedAt());
    }
}
