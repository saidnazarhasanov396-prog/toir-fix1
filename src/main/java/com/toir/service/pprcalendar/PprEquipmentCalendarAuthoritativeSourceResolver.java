package com.toir.service.pprcalendar;

import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarAuthoritativeSource;
import com.toir.entity.PprPlan;
import com.toir.enums.MaterializationMode;
import com.toir.enums.TaskMaterializationStatus;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class PprEquipmentCalendarAuthoritativeSourceResolver {

    public PprEquipmentCalendarSourceSelection resolve(PprPlan plan) {
        Objects.requireNonNull(plan, "plan is required");
        if (plan.getMaterializationMode() == MaterializationMode.APPROVAL_FIRST
                && plan.getTaskMaterializationStatus() == TaskMaterializationStatus.NOT_MATERIALIZED
                && plan.getCalculationRevision() != null) {
            return new PprEquipmentCalendarSourceSelection(
                    PprEquipmentCalendarAuthoritativeSource.CALCULATION_SNAPSHOT,
                    plan.getCalculationRevision());
        }
        return new PprEquipmentCalendarSourceSelection(
                PprEquipmentCalendarAuthoritativeSource.PPR_TASK,
                null);
    }
}
