package com.toir.dto.maintenancedue;

import com.toir.entity.maintenance.MaintenanceDueEvent;
import com.toir.enums.MaintenanceDueEventStatus;
import com.toir.enums.MaintenanceDueStatus;
import com.toir.enums.MaintenanceTriggerSource;
import com.toir.enums.MeterType;
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
        return new MaintenanceDueEventDto(
                event.getId(),
                event.getEquipmentId(),
                event.getRegulationId(),
                event.getEquipmentMaintenanceRuleId(),
                event.getTemplateId(),
                event.getStatus(),
                event.getDueStatus(),
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
                event.getExplanation(),
                equipment,
                regulation
        );
    }
}
