package com.toir.service.pprcalendar;

import com.toir.entity.PprPlan;
import com.toir.entity.PprTask;
import com.toir.enums.MaterializationMode;
import com.toir.enums.PlanStatus;
import com.toir.enums.PprPlanOrigin;
import com.toir.enums.TaskMaterializationStatus;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PprOperationalCalendarPolicy {

    private static final Set<PlanStatus> OPERATIONAL_STATUSES =
            EnumSet.of(
                    PlanStatus.APPROVED,
                    PlanStatus.IN_PROGRESS,
                    PlanStatus.CLOSED,
                    PlanStatus.CANCELLED);

    public boolean includes(PprPlan plan) {
        return plan != null
                && !plan.isDeleted()
                && plan.getOrigin() == PprPlanOrigin.MAINTENANCE_SCHEDULE
                && plan.getMaterializationMode() == MaterializationMode.APPROVAL_FIRST
                && plan.getTaskMaterializationStatus()
                        == TaskMaterializationStatus.MATERIALIZED
                && OPERATIONAL_STATUSES.contains(plan.getStatus())
                && plan.getCalculationRevision() != null
                && Objects.equals(
                        plan.getCalculationRevision(), plan.getApprovedRevision())
                && Objects.equals(
                        plan.getApprovedRevision(), plan.getMaterializedRevision())
                && plan.getMaterializedTaskCount() != null;
    }

    public boolean includes(PprTask task) {
        return task != null
                && !task.isDeleted()
                && task.getSourceCalculationItemId() != null
                && includes(task.getPlan());
    }
}
