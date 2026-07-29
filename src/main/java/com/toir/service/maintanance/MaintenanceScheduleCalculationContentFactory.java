package com.toir.service.maintanance;

import com.toir.entity.PprPlan;
import com.toir.entity.PprPlanTarget;
import com.toir.entity.maintenance.MaintenanceScheduleCalculationItem;
import com.toir.enums.MaintenanceScheduleScopeType;
import com.toir.enums.PprTargetType;
import java.util.List;

public class MaintenanceScheduleCalculationContentFactory {

    public MaintenanceScheduleCalculationContent fromSnapshot(
            PprPlan plan,
            long revision,
            List<MaintenanceScheduleCalculationItem> items) {
        List<PprPlanTarget> targets = plan.getTargets().stream()
                .filter(target -> !target.isDeleted())
                .toList();
        MaintenanceScheduleScopeType selectionScope = targets.stream()
                .anyMatch(target ->
                        target.getTargetType() == PprTargetType.EQUIPMENT)
                ? MaintenanceScheduleScopeType.EQUIPMENT
                : MaintenanceScheduleScopeType.EQUIPMENT_TYPE;
        return new MaintenanceScheduleCalculationContent(
                plan.getName(),
                plan.getNotes(),
                plan.getStartDate(),
                plan.getEndDate(),
                selectionScope,
                plan.getScopeType(),
                plan.getDepartmentId(),
                targets.stream()
                        .map(PprPlanTarget::getEquipmentId)
                        .filter(java.util.Objects::nonNull)
                        .toList(),
                targets.stream()
                        .map(PprPlanTarget::getEquipmentTypeId)
                        .filter(java.util.Objects::nonNull)
                        .toList(),
                targets.stream()
                        .map(PprPlanTarget::getRegulationId)
                        .filter(java.util.Objects::nonNull)
                        .toList(),
                plan.getAnchorMode(),
                plan.getRecurrenceAnchor(),
                plan.isShiftFromExcludedWeekdays(),
                plan.getExcludedWeekdays(),
                revision,
                items.stream()
                        .map(MaintenanceScheduleCalculationContentMapper::fromSnapshot)
                        .toList());
    }
}
