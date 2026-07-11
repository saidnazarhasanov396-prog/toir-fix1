package com.toir.service.plannedshutdown;

import com.toir.enums.PlannedShutdownStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlannedShutdownTransitionPolicyTest {

    private final PlannedShutdownTransitionPolicy policy = new PlannedShutdownTransitionPolicy();

    @Test
    void allowsEveryCanonicalForwardEdge() {
        List<Edge> edges = List.of(
                edge(PlannedShutdownStatus.DRAFT, PlannedShutdownStatus.SCOPE_FORMATION),
                edge(PlannedShutdownStatus.SCOPE_FORMATION, PlannedShutdownStatus.READINESS_CHECK),
                edge(PlannedShutdownStatus.READINESS_CHECK, PlannedShutdownStatus.PENDING_APPROVAL),
                edge(PlannedShutdownStatus.PENDING_APPROVAL, PlannedShutdownStatus.APPROVED),
                edge(PlannedShutdownStatus.APPROVED, PlannedShutdownStatus.PREPARATION),
                edge(PlannedShutdownStatus.PREPARATION, PlannedShutdownStatus.SHUTDOWN_STARTED),
                edge(PlannedShutdownStatus.SHUTDOWN_STARTED, PlannedShutdownStatus.SAFE_STATE),
                edge(PlannedShutdownStatus.SAFE_STATE, PlannedShutdownStatus.REPAIR_IN_PROGRESS),
                edge(PlannedShutdownStatus.REPAIR_IN_PROGRESS, PlannedShutdownStatus.TESTING),
                edge(PlannedShutdownStatus.TESTING, PlannedShutdownStatus.STARTUP),
                edge(PlannedShutdownStatus.STARTUP, PlannedShutdownStatus.COMPLETED),
                edge(PlannedShutdownStatus.COMPLETED, PlannedShutdownStatus.CLOSED));

        assertThat(edges).allSatisfy(edge -> assertThat(policy.canTransition(edge.from, edge.to)).isTrue());
    }

    @Test
    void rejectsSkippedBackwardAndTerminalTransitions() {
        assertThat(policy.canTransition(PlannedShutdownStatus.DRAFT, PlannedShutdownStatus.APPROVED)).isFalse();
        assertThat(policy.canTransition(PlannedShutdownStatus.TESTING, PlannedShutdownStatus.REPAIR_IN_PROGRESS)).isFalse();
        assertThat(policy.canTransition(PlannedShutdownStatus.CLOSED, PlannedShutdownStatus.CANCELLED)).isFalse();
        assertThat(policy.canTransition(PlannedShutdownStatus.CANCELLED, PlannedShutdownStatus.DRAFT)).isFalse();
    }

    @Test
    void cancellationAndRescheduleAreExplicitlyBounded() {
        assertThat(policy.canCancel(PlannedShutdownStatus.PREPARATION)).isTrue();
        assertThat(policy.canCancel(PlannedShutdownStatus.SHUTDOWN_STARTED)).isFalse();
        assertThat(policy.canReschedule(PlannedShutdownStatus.APPROVED)).isTrue();
        assertThat(policy.canReschedule(PlannedShutdownStatus.SHUTDOWN_STARTED)).isFalse();
        assertThat(policy.canExtend(PlannedShutdownStatus.REPAIR_IN_PROGRESS)).isTrue();
        assertThat(policy.canExtend(PlannedShutdownStatus.APPROVED)).isFalse();
    }

    private static Edge edge(PlannedShutdownStatus from, PlannedShutdownStatus to) {
        return new Edge(from, to);
    }

    private record Edge(PlannedShutdownStatus from, PlannedShutdownStatus to) {}
}
