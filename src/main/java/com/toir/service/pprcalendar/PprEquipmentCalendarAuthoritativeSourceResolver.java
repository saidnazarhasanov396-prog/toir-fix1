package com.toir.service.pprcalendar;

import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarAuthoritativeSource;
import com.toir.entity.PprPlan;
import com.toir.enums.MaterializationMode;
import com.toir.enums.PlanStatus;
import com.toir.enums.TaskMaterializationStatus;
import com.toir.exception.RestException;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class PprEquipmentCalendarAuthoritativeSourceResolver {

    public PprEquipmentCalendarSourceSelection resolve(PprPlan plan) {
        Objects.requireNonNull(plan, "plan is required");
        if (plan.getMaterializationMode() == MaterializationMode.LEGACY_MATERIALIZED) {
            if (plan.getTaskMaterializationStatus()
                    != TaskMaterializationStatus.NOT_APPLICABLE) {
                throw integrityError();
            }
            return new PprEquipmentCalendarSourceSelection(
                    PprEquipmentCalendarAuthoritativeSource.PPR_TASK,
                    null);
        }
        if (plan.getMaterializationMode() != MaterializationMode.APPROVAL_FIRST) {
            throw integrityError();
        }
        if (plan.getTaskMaterializationStatus()
                == TaskMaterializationStatus.NOT_MATERIALIZED) {
            if (plan.getStatus() != PlanStatus.CALCULATED
                    || plan.getCalculationRevision() == null
                    || plan.getCalculationRevision() < 1
                    || plan.getMaterializedRevision() != null) {
                throw integrityError();
            }
            return new PprEquipmentCalendarSourceSelection(
                    PprEquipmentCalendarAuthoritativeSource.CALCULATION_SNAPSHOT,
                    plan.getCalculationRevision());
        }
        if (plan.getTaskMaterializationStatus()
                == TaskMaterializationStatus.MATERIALIZED
                && isMaterializedStatus(plan.getStatus())
                && plan.getMaterializedRevision() != null
                && Objects.equals(
                        plan.getMaterializedRevision(),
                        plan.getApprovedRevision())
                && Objects.equals(
                        plan.getApprovedRevision(),
                        plan.getCalculationRevision())
                && Objects.equals(
                        plan.getApprovedContentHash(),
                        plan.getCalculationContentHash())
                && Objects.equals(
                        plan.getApprovedContentHashVersion(),
                        plan.getCalculationContentHashVersion())) {
            return new PprEquipmentCalendarSourceSelection(
                    PprEquipmentCalendarAuthoritativeSource.PPR_TASK,
                    plan.getMaterializedRevision());
        }
        throw integrityError();
    }

    private boolean isMaterializedStatus(PlanStatus status) {
        return status == PlanStatus.APPROVED
                || status == PlanStatus.IN_PROGRESS
                || status == PlanStatus.CLOSED
                || status == PlanStatus.CANCELLED;
    }

    private RestException integrityError() {
        return new RestException(
                "PPR equipment calendar source metadata is inconsistent",
                org.springframework.http.HttpStatus.CONFLICT,
                "PPR_CALENDAR_SOURCE_INTEGRITY");
    }
}
