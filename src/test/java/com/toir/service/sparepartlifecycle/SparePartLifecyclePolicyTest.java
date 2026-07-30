package com.toir.service.sparepartlifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.toir.entity.sparepartlifecycle.SparePartDueEvent;
import com.toir.entity.sparepartlifecycle.SparePartInstallation;
import com.toir.enums.sparepartlifecycle.SparePartDueAction;
import com.toir.enums.sparepartlifecycle.SparePartDueEventState;
import com.toir.enums.sparepartlifecycle.SparePartInstallationStatus;
import com.toir.enums.sparepartlifecycle.SparePartLifecycleEvaluationState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SparePartLifecyclePolicyTest {

    private final SparePartLifecyclePolicy policy = new SparePartLifecyclePolicy();

    @Test
    void acknowledgedUnresolvedEventRemainsInAttention() {
        SparePartInstallation installation = installation(SparePartInstallationStatus.ACTIVE);
        SparePartDueEvent event = event(installation, SparePartDueAction.MAINTENANCE_REQUIRED,
                SparePartDueEventState.DUE);
        event.setAcknowledgedAt(Instant.parse("2026-07-30T08:00:00Z"));

        var result = policy.assess(List.of(installation), List.of(event));

        assertThat(result.attentionCount()).isEqualTo(1);
        assertThat(result.acknowledgedAttentionCount()).isEqualTo(1);
        assertThat(result.maintenanceRequiredCount()).isEqualTo(1);
    }

    @Test
    void removedAndReplacedInstallationsAreExcludedFromCurrentCounters() {
        SparePartInstallation removed = installation(SparePartInstallationStatus.REMOVED);
        SparePartInstallation replaced = installation(SparePartInstallationStatus.REPLACED);

        var result = policy.assess(List.of(removed, replaced), List.of(
                event(removed, SparePartDueAction.BLOCK_OPERATION, SparePartDueEventState.OVERDUE),
                event(replaced, SparePartDueAction.BLOCK_OPERATION, SparePartDueEventState.OVERDUE)));

        assertThat(result.installedCount()).isZero();
        assertThat(result.attentionCount()).isZero();
        assertThat(result.blockedCount()).isZero();
    }

    @Test
    void duplicateEventsCountOneInstallationAtHighestSeverity() {
        SparePartInstallation installation = installation(SparePartInstallationStatus.ACTIVE);

        var result = policy.assess(List.of(installation), List.of(
                event(installation, SparePartDueAction.WARNING_ONLY, SparePartDueEventState.WARNING),
                event(installation, SparePartDueAction.BLOCK_OPERATION, SparePartDueEventState.OVERDUE)));

        assertThat(result.attentionCount()).isEqualTo(1);
        assertThat(result.blockedCount()).isEqualTo(1);
        assertThat(result.warningCount()).isZero();
    }

    private static SparePartInstallation installation(SparePartInstallationStatus status) {
        SparePartInstallation installation = new SparePartInstallation();
        installation.setId(UUID.randomUUID());
        installation.setStatus(status);
        installation.setLifecycleEvaluationState(SparePartLifecycleEvaluationState.OK);
        return installation;
    }

    private static SparePartDueEvent event(SparePartInstallation installation,
                                           SparePartDueAction action,
                                           SparePartDueEventState state) {
        SparePartDueEvent event = new SparePartDueEvent();
        event.setId(UUID.randomUUID());
        event.setInstallationId(installation.getId());
        event.setDueAction(action);
        event.setState(state);
        return event;
    }
}
