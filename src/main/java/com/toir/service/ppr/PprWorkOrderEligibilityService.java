package com.toir.service.ppr;

import com.toir.entity.PprTask;
import com.toir.enums.PlanStatus;
import com.toir.enums.PprTaskStatus;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class PprWorkOrderEligibilityService {

    private static final Set<PlanStatus> EXECUTABLE_PLAN_STATUSES =
            Set.of(PlanStatus.APPROVED, PlanStatus.IN_PROGRESS);

    public Eligibility evaluate(PprTask task, LocalDateTime now, boolean workOrderExists) {
        if (task == null || task.getPlan() == null
                || !EXECUTABLE_PLAN_STATUSES.contains(task.getPlan().getStatus())) {
            return Eligibility.rejected("PLAN_STATUS_NOT_EXECUTABLE");
        }
        if (task.getStatus() != PprTaskStatus.APPROVED) {
            return Eligibility.rejected("TASK_STATUS_NOT_APPROVED");
        }
        if (task.getScheduledStart() == null) {
            return Eligibility.rejected("TASK_SCHEDULE_MISSING");
        }
        if (task.getWorkOrderLeadDays() < 0 || task.getWorkOrderLeadDays() > 365) {
            return Eligibility.rejected("WORK_ORDER_LEAD_DAYS_INVALID");
        }
        if (workOrderExists) {
            return Eligibility.rejected("WORK_ORDER_ALREADY_EXISTS");
        }
        LocalDateTime generationAt = task.getScheduledStart().minusDays(task.getWorkOrderLeadDays());
        if (now.isBefore(generationAt)) {
            return Eligibility.rejected("GENERATION_TIME_NOT_REACHED");
        }
        return Eligibility.allowed();
    }

    public record Eligibility(boolean eligible, String reason) {
        private static Eligibility allowed() {
            return new Eligibility(true, null);
        }

        private static Eligibility rejected(String reason) {
            return new Eligibility(false, reason);
        }
    }
}
