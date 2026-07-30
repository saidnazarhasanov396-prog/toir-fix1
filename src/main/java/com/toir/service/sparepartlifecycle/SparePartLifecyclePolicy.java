package com.toir.service.sparepartlifecycle;

import com.toir.entity.sparepartlifecycle.SparePartDueEvent;
import com.toir.entity.sparepartlifecycle.SparePartInstallation;
import com.toir.enums.sparepartlifecycle.SparePartDueAction;
import com.toir.enums.sparepartlifecycle.SparePartDueEventState;
import com.toir.enums.sparepartlifecycle.SparePartInstallationStatus;
import com.toir.enums.sparepartlifecycle.SparePartLifecycleEvaluationState;
import com.toir.enums.sparepartlifecycle.SparePartOperationalReadiness;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Canonical W0 policy for spare-part readiness and attention counters. */
@Component
public class SparePartLifecyclePolicy {

    public Assessment assess(List<SparePartInstallation> installations, List<SparePartDueEvent> events) {
        Map<UUID, SparePartInstallation> active = new HashMap<>();
        int evaluationErrorCount = 0;
        for (SparePartInstallation installation : installations) {
            if (installation.getStatus() != SparePartInstallationStatus.ACTIVE) {
                continue;
            }
            active.put(installation.getId(), installation);
            if (installation.getLifecycleEvaluationState() == SparePartLifecycleEvaluationState.ERROR) {
                evaluationErrorCount++;
            }
        }
        Map<UUID, Severity> attention = new HashMap<>();
        Map<UUID, Boolean> acknowledged = new HashMap<>();
        for (SparePartDueEvent event : events) {
            if (!active.containsKey(event.getInstallationId()) || event.getState() == SparePartDueEventState.RESOLVED) {
                continue;
            }
            Severity severity = severity(event);
            if (severity == Severity.NONE) {
                continue;
            }
            attention.merge(event.getInstallationId(), severity, Severity::max);
            if (event.getAcknowledgedAt() != null) {
                acknowledged.put(event.getInstallationId(), true);
            }
        }
        int warning = 0;
        int maintenance = 0;
        int blocked = 0;
        for (Severity severity : attention.values()) {
            switch (severity) {
                case WARNING -> warning++;
                case MAINTENANCE_REQUIRED -> maintenance++;
                case BLOCKED -> blocked++;
                case NONE -> { }
            }
        }
        SparePartOperationalReadiness readiness = blocked > 0
                ? SparePartOperationalReadiness.BLOCKED
                : maintenance > 0
                ? SparePartOperationalReadiness.MAINTENANCE_REQUIRED
                : warning > 0
                ? SparePartOperationalReadiness.WARNING
                : SparePartOperationalReadiness.READY;
        int acknowledgedCount = (int) acknowledged.keySet().stream().filter(attention::containsKey).count();
        return new Assessment(readiness, active.size(), attention.size(), warning, maintenance, blocked,
                acknowledgedCount, evaluationErrorCount > 0, evaluationErrorCount);
    }

    public boolean isAttention(SparePartDueEvent event) {
        return event.getState() != SparePartDueEventState.RESOLVED && severity(event) != Severity.NONE;
    }

    private Severity severity(SparePartDueEvent event) {
        if (event.getState() == SparePartDueEventState.UPCOMING
                || event.getState() == SparePartDueEventState.WARNING) {
            return Severity.WARNING;
        }
        if (event.getState() != SparePartDueEventState.DUE
                && event.getState() != SparePartDueEventState.OVERDUE) {
            return Severity.NONE;
        }
        return event.getDueAction() == SparePartDueAction.BLOCK_OPERATION
                ? Severity.BLOCKED
                : event.getDueAction() == SparePartDueAction.MAINTENANCE_REQUIRED
                ? Severity.MAINTENANCE_REQUIRED
                : Severity.WARNING;
    }

    private enum Severity {
        NONE, WARNING, MAINTENANCE_REQUIRED, BLOCKED;
        private static Severity max(Severity left, Severity right) {
            return left.ordinal() >= right.ordinal() ? left : right;
        }
    }

    public record Assessment(
            SparePartOperationalReadiness readinessStatus,
            int installedCount,
            int attentionCount,
            int warningCount,
            int maintenanceRequiredCount,
            int blockedCount,
            int acknowledgedAttentionCount,
            boolean hasEvaluationError,
            int evaluationErrorCount
    ) { }
}
