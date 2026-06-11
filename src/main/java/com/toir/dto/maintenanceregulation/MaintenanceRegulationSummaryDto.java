package com.toir.dto.maintenanceregulation;

import com.toir.entity.maintenance.EquipmentMaintenanceRule;
import com.toir.entity.maintenance.MaintenanceRegulation;
import java.util.UUID;

public record MaintenanceRegulationSummaryDto(
        UUID id,
        String code,
        String name,
        String category,
        Integer defaultDurationHours,
        String requiredSkill,
        String safetyNotes,
        String toolsRequired,
        String sparePartsRequired,
        String consumablesRequired,
        Boolean active
) {
    public static MaintenanceRegulationSummaryDto from(MaintenanceRegulation regulation,
                                                       OperationSummary operationSummary) {
        if (regulation == null) {
            return null;
        }
        return new MaintenanceRegulationSummaryDto(
                regulation.getId(),
                regulation.getCode(),
                regulation.getName(),
                regulation.getMaintenanceKind() == null ? null : regulation.getMaintenanceKind().name(),
                durationHours(regulation),
                operationSummary == null ? null : operationSummary.requiredSkill(),
                operationSummary == null ? null : operationSummary.safetyNotes(),
                operationSummary == null ? null : operationSummary.toolsRequired(),
                operationSummary == null ? null : operationSummary.sparePartsRequired(),
                operationSummary == null ? null : operationSummary.consumablesRequired(),
                regulation.isActive()
        );
    }

    public static MaintenanceRegulationSummaryDto from(EquipmentMaintenanceRule rule,
                                                       OperationSummary operationSummary) {
        if (rule == null) {
            return null;
        }
        return new MaintenanceRegulationSummaryDto(
                rule.getId(),
                rule.getCode(),
                rule.getName(),
                rule.getMaintenanceKind() == null ? null : rule.getMaintenanceKind().name(),
                durationHours(rule),
                operationSummary == null ? null : operationSummary.requiredSkill(),
                operationSummary == null ? null : operationSummary.safetyNotes(),
                operationSummary == null ? null : operationSummary.toolsRequired(),
                operationSummary == null ? null : operationSummary.sparePartsRequired(),
                operationSummary == null ? null : operationSummary.consumablesRequired(),
                rule.isActive()
        );
    }

    private static Integer durationHours(MaintenanceRegulation regulation) {
        if (regulation == null) {
            return null;
        }
        double hours = regulation.getNormativeLaborHours();
        return hours <= 0 ? null : (int) Math.ceil(hours);
    }

    private static Integer durationHours(EquipmentMaintenanceRule rule) {
        if (rule == null) {
            return null;
        }
        double hours = rule.getNormativeLaborHours();
        return hours <= 0 ? null : (int) Math.ceil(hours);
    }

    public record OperationSummary(
            String requiredSkill,
            String safetyNotes,
            String toolsRequired,
            String sparePartsRequired,
            String consumablesRequired
    ) {}
}
