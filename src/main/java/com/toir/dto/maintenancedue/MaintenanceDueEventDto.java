package com.toir.dto.maintenancedue;

import com.toir.entity.maintenance.MaintenanceDueEvent;
import com.toir.enums.MaintenanceDueEventStatus;
import com.toir.enums.MaintenanceDueStatus;
import com.toir.enums.MaintenanceTriggerSource;
import com.toir.enums.MeterType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MaintenanceDueEventDto(
        UUID id,
        UUID equipmentId,
        UUID regulationId,
        UUID equipmentMaintenanceRuleId,
        UUID templateId,
        MaintenanceDueEventStatus status,
        MaintenanceDueStatus dueStatus,
        MaintenanceTriggerSource triggerSource,
        String cycleKey,
        Instant dueAt,
        MeterType meterType,
        Double meterCurrentValue,
        Double meterAnchorValue,
        Double meterInterval,
        Double meterRemaining,
        UUID createdTaskId,
        UUID createdWorkOrderId,
        Instant detectedAt,
        Instant resolvedAt,
        String resolutionReason,
        String explanation,
        Ref equipment,
        Ref regulation
) {
    public record Ref(UUID id, String code, String name) {}

    public static MaintenanceDueEventDto from(MaintenanceDueEvent event, Ref equipment, Ref regulation) {
        MaintenanceDueStatus dueStatus = normalizedDueStatus(event);
        String explanation = normalizedExplanation(event, dueStatus);
        return new MaintenanceDueEventDto(
                event.getId(),
                event.getEquipmentId(),
                event.getRegulationId(),
                event.getEquipmentMaintenanceRuleId(),
                event.getTemplateId(),
                event.getStatus(),
                dueStatus,
                event.getTriggerSource(),
                event.getCycleKey(),
                event.getDueAt(),
                event.getMeterType(),
                event.getMeterCurrentValue(),
                event.getMeterAnchorValue(),
                event.getMeterInterval(),
                event.getMeterRemaining(),
                event.getCreatedTaskId(),
                event.getCreatedWorkOrderId(),
                event.getDetectedAt(),
                event.getResolvedAt(),
                event.getResolutionReason(),
                explanation,
                equipment,
                regulation
        );
    }

    private static MaintenanceDueStatus normalizedDueStatus(MaintenanceDueEvent event) {
        if (!meterDominant(event)) {
            return event.getDueStatus();
        }
        int remaining = BigDecimal.valueOf(event.getMeterRemaining()).compareTo(BigDecimal.ZERO);
        if (remaining < 0) {
            return MaintenanceDueStatus.OVERDUE;
        }
        if (remaining == 0) {
            return MaintenanceDueStatus.DUE;
        }
        return event.getDueStatus();
    }

    private static String normalizedExplanation(MaintenanceDueEvent event, MaintenanceDueStatus dueStatus) {
        if (!meterDominant(event)) {
            return event.getExplanation();
        }
        if (dueStatus == MaintenanceDueStatus.OVERDUE) {
            return replaceMeterExplanation(event.getExplanation(), "Meter trigger overdue");
        }
        if (dueStatus == MaintenanceDueStatus.DUE) {
            return replaceMeterExplanation(event.getExplanation(), "Meter trigger due");
        }
        return event.getExplanation();
    }

    private static boolean meterDominant(MaintenanceDueEvent event) {
        if (event.getMeterType() == null || event.getMeterRemaining() == null) {
            return false;
        }
        if (event.getTriggerSource() == MaintenanceTriggerSource.METER_READING) {
            return true;
        }
        String explanation = event.getExplanation();
        return explanation == null || explanation.isBlank() || explanation.startsWith("Meter trigger");
    }

    private static String replaceMeterExplanation(String explanation, String replacement) {
        if (explanation == null || explanation.isBlank()) {
            return replacement;
        }
        return explanation
                .replace("Meter trigger overdue", replacement)
                .replace("Meter trigger upcoming", replacement)
                .replace("Meter trigger not due", replacement)
                .replace("Meter trigger due", replacement);
    }
}
