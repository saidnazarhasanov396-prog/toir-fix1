package com.toir.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AnnualMaintenanceApprovalFirstEnumTest {

    @Test
    void planStatusAddsCalculatedWithoutChangingGenerated() {
        assertThat(PlanStatus.valueOf("CALCULATED")).isEqualTo(PlanStatus.CALCULATED);
        assertThat(PlanStatus.valueOf("GENERATED")).isEqualTo(PlanStatus.GENERATED);
    }

    @Test
    void supersededApprovalIsTerminalAndNotActionable() {
        assertThat(ApprovalStatus.SUPERSEDED.isTerminal()).isTrue();
        assertThat(ApprovalStatus.SUPERSEDED.isPending()).isFalse();
        assertThat(ApprovalStatus.SUPERSEDED.isActionable()).isFalse();
        assertThat(ApprovalStatus.PENDING.isPending()).isTrue();
        assertThat(ApprovalStatus.PENDING.isActionable()).isTrue();
    }

    @Test
    void materializationModeUsesLegacySafeDefaultAndExplicitApprovalFirstValue() {
        assertThat(MaterializationMode.values()).containsExactly(
                MaterializationMode.LEGACY_MATERIALIZED,
                MaterializationMode.APPROVAL_FIRST
        );
    }

    @Test
    void taskMaterializationStatusContainsOnlyV1Values() {
        assertThat(TaskMaterializationStatus.values()).containsExactly(
                TaskMaterializationStatus.NOT_APPLICABLE,
                TaskMaterializationStatus.NOT_MATERIALIZED,
                TaskMaterializationStatus.MATERIALIZED
        );
    }

    @Test
    void resolutionCodesAreTypedAndCompleteForFoundationSlice() {
        assertThat(ApprovalResolutionCode.values()).containsExactly(
                ApprovalResolutionCode.USER_CANCELLED,
                ApprovalResolutionCode.RETURNED_FOR_REWORK,
                ApprovalResolutionCode.SYSTEM_CANCELLED,
                ApprovalResolutionCode.TARGET_DELETED,
                ApprovalResolutionCode.REVIEW_AMEND,
                ApprovalResolutionCode.STALE_SCOPE,
                ApprovalResolutionCode.NEW_REVISION,
                ApprovalResolutionCode.ROUTE_CHANGED
        );
    }
}
