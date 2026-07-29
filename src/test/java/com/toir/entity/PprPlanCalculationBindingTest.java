package com.toir.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toir.enums.MaterializationMode;
import com.toir.exception.MaintenanceScheduleCalculationConflictException;
import com.toir.exception.MaintenanceScheduleCalculationConflictException.Reason;
import org.junit.jupiter.api.Test;

class PprPlanCalculationBindingTest {

    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);

    @Test
    void approvalFirstPlanInitializesAndAdvancesExactlyOneRevision() {
        PprPlan plan = approvalFirstPlan();

        plan.initializeApprovalFirstCalculation(1L, 1, HASH_A);
        assertThat(plan.matchesCalculationBinding(1L, 1, HASH_A)).isTrue();

        plan.advanceApprovalFirstCalculation(1L, 2L, 1, HASH_B);

        assertThat(plan.getCalculationRevision()).isEqualTo(2L);
        assertThat(plan.getCalculationContentHashVersion()).isEqualTo(1);
        assertThat(plan.getCalculationContentHash()).isEqualTo(HASH_B);
        assertThat(plan.matchesCalculationBinding(2L, 1, HASH_B)).isTrue();
        assertThat(plan.matchesCalculationBinding(2L, 1, HASH_A)).isFalse();
    }

    @Test
    void legacyPlanRejectsApprovalFirstRevisionMutation() {
        PprPlan plan = new PprPlan();

        assertReason(
                () -> plan.initializeApprovalFirstCalculation(1L, 1, HASH_A),
                Reason.INVALID_REVISION_TRANSITION
        );
    }

    @Test
    void initializationRejectsNonInitialRevisionAndInvalidHashBinding() {
        PprPlan plan = approvalFirstPlan();

        assertReason(
                () -> plan.initializeApprovalFirstCalculation(2L, 1, HASH_A),
                Reason.INVALID_REVISION_TRANSITION
        );
        assertReason(
                () -> plan.initializeApprovalFirstCalculation(1L, 0, HASH_A),
                Reason.HASH_VERSION_UNSUPPORTED
        );
        assertReason(
                () -> plan.initializeApprovalFirstCalculation(1L, 1, "ABC"),
                Reason.INVALID_HASH
        );
    }

    @Test
    void sameRevisionCannotSilentlyReplaceItsHash() {
        PprPlan plan = approvalFirstPlan();
        plan.initializeApprovalFirstCalculation(1L, 1, HASH_A);

        assertReason(
                () -> plan.initializeApprovalFirstCalculation(1L, 1, HASH_B),
                Reason.HASH_MISMATCH
        );
        assertReason(
                () -> plan.initializeApprovalFirstCalculation(1L, 1, HASH_A),
                Reason.REVISION_CONFLICT
        );
    }

    @Test
    void advanceRejectsStaleExpectedRevisionAndSkippedRevision() {
        PprPlan plan = approvalFirstPlan();
        plan.initializeApprovalFirstCalculation(1L, 1, HASH_A);

        assertReason(
                () -> plan.advanceApprovalFirstCalculation(0L, 2L, 1, HASH_B),
                Reason.REVISION_CONFLICT
        );
        assertReason(
                () -> plan.advanceApprovalFirstCalculation(1L, 3L, 1, HASH_B),
                Reason.INVALID_REVISION_TRANSITION
        );
    }

    private static PprPlan approvalFirstPlan() {
        PprPlan plan = new PprPlan();
        plan.setMaterializationMode(MaterializationMode.APPROVAL_FIRST);
        return plan;
    }

    private static void assertReason(Runnable operation, Reason expectedReason) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(MaintenanceScheduleCalculationConflictException.class)
                .satisfies(error -> assertThat(
                        ((MaintenanceScheduleCalculationConflictException) error).getReason())
                        .isEqualTo(expectedReason));
    }
}
