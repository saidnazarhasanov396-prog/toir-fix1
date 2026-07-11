package com.toir.service.plannedshutdown;

import com.toir.enums.PlannedShutdownStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class PlannedShutdownReadinessLifecyclePolicyTest {
    private final PlannedShutdownReadinessLifecyclePolicy policy = new PlannedShutdownReadinessLifecyclePolicy();

    @ParameterizedTest
    @EnumSource(PlannedShutdownStatus.class)
    void exposesExactLifecycleCapabilities(PlannedShutdownStatus status) {
        assertThat(policy.canEditReadinessDefinition(status)).isEqualTo(switch (status) {
            case DRAFT, SCOPE_FORMATION, READINESS_CHECK -> true; default -> false;
        });
        assertThat(policy.canActOnReadiness(status)).isEqualTo(switch (status) {
            case READINESS_CHECK, PREPARATION -> true; default -> false;
        });
        assertThat(policy.canEditIsolationDefinition(status)).isEqualTo(switch (status) {
            case DRAFT, SCOPE_FORMATION, READINESS_CHECK, PENDING_APPROVAL, APPROVED, PREPARATION,
                    SHUTDOWN_STARTED -> true; default -> false;
        });
        assertThat(policy.canApplyIsolation(status)).isEqualTo(
                status == PlannedShutdownStatus.PREPARATION || status == PlannedShutdownStatus.SHUTDOWN_STARTED);
        assertThat(policy.canVerifyIsolation(status)).isEqualTo(status == PlannedShutdownStatus.SHUTDOWN_STARTED);
        assertThat(policy.canReleaseIsolation(status)).isEqualTo(
                status == PlannedShutdownStatus.STARTUP || status == PlannedShutdownStatus.COMPLETED);
    }
}
