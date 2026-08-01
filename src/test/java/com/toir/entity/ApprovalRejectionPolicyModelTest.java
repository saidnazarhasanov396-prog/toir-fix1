package com.toir.entity;

import com.toir.enums.ApprovalRejectionPolicy;
import com.toir.enums.ApprovalStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalRejectionPolicyModelTest {

    @Test
    void policyValuesAndEntityDefaultsAreBackwardCompatible() {
        assertThat(ApprovalRejectionPolicy.values()).containsExactly(
                ApprovalRejectionPolicy.TERMINATE,
                ApprovalRejectionPolicy.RETURN_TO_PREVIOUS_STEP,
                ApprovalRejectionPolicy.RETURN_TO_INITIATOR,
                ApprovalRejectionPolicy.MAJORITY);
        assertThat(new ApprovalTemplate().getRejectionPolicy())
                .isEqualTo(ApprovalRejectionPolicy.TERMINATE);
        assertThat(new ApprovalRequest().getRejectionPolicy())
                .isEqualTo(ApprovalRejectionPolicy.TERMINATE);
    }

    @Test
    void reworkIsNonterminalAndNotApproverActionable() {
        assertThat(ApprovalStatus.REWORK.isTerminal()).isFalse();
        assertThat(ApprovalStatus.REWORK.isActionable()).isFalse();
        assertThat(ApprovalStatus.REWORK.isPending()).isFalse();
    }
}
