package com.toir.service.plannedshutdown;

import com.toir.dto.plannedshutdown.PlannedShutdownBlocker;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlannedShutdownReadinessPolicyTest {

    private final PlannedShutdownReadinessPolicy policy = new PlannedShutdownReadinessPolicy();

    @Test
    void returnsEveryFailClosedBlockerInDeterministicOrder() {
        UUID shutdownId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();
        UUID isolationId = UUID.randomUUID();
        var facts = new PlannedShutdownReadinessPolicy.Facts(
                shutdownId, false, false, false,
                List.of(new PlannedShutdownReadinessPolicy.WorkFact(workId, true, true, false, false)),
                false, false, false, false,
                List.of(new PlannedShutdownReadinessPolicy.IsolationFact(isolationId, false, false, false)),
                false, false, Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-01T01:00:00Z"), Instant.parse("2026-08-01T02:00:00Z"));

        var result = policy.evaluateReadiness(facts);

        assertThat(result.canProceed()).isFalse();
        assertThat(result.blockers()).extracting(PlannedShutdownBlocker::code).containsExactly(
                "APPROVAL_HSE_MISSING", "APPROVAL_PRODUCTION_MISSING", "APPROVAL_SCOPE_STALE",
                "BOUNDARY_MISSING", "ISOLATION_POINT_MISSING", "MATERIAL_RESERVATION_MISSING",
                "OWNER_MISSING", "PERFORMER_OR_CONTRACTOR_MISSING", "PERMIT_INACTIVE",
                "READINESS_CRITICAL_INCOMPLETE", "WINDOW_OUTSIDE_APPROVED", "WORK_MISSING");
    }

    @Test
    void safeStateRequiresEveryRequiredIsolationAppliedAndVerified() {
        UUID pointId = UUID.randomUUID();
        var facts = readyFacts(List.of(new PlannedShutdownReadinessPolicy.IsolationFact(pointId, true, true, false)));

        var result = policy.evaluateSafeState(facts);

        assertThat(result.canProceed()).isFalse();
        assertThat(result.blockers()).extracting(PlannedShutdownBlocker::code)
                .containsExactly("ISOLATION_NOT_VERIFIED");
        assertThat(result.blockers().get(0).entityId()).isEqualTo(pointId);
    }

    @Test
    void readyFactsProceedAndWarningsDoNotBlock() {
        var result = policy.evaluateSafeState(readyFacts(List.of(
                new PlannedShutdownReadinessPolicy.IsolationFact(UUID.randomUUID(), true, true, true))));

        assertThat(result.canProceed()).isTrue();
        assertThat(result.blockers()).isEmpty();
    }

    private static PlannedShutdownReadinessPolicy.Facts readyFacts(
            List<PlannedShutdownReadinessPolicy.IsolationFact> isolation) {
        Instant now = Instant.parse("2026-08-01T01:00:00Z");
        return new PlannedShutdownReadinessPolicy.Facts(UUID.randomUUID(), true, true, true,
                List.of(new PlannedShutdownReadinessPolicy.WorkFact(UUID.randomUUID(), true, true, true, true)),
                true, true, true, true, isolation, true, true,
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-01T02:00:00Z"), now);
    }
}
