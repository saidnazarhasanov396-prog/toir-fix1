package com.toir.finance;

import com.toir.entity.projects.BudgetLine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FinanceCanonicalModelTest {

    @Test
    void availableAmount_matchesBudgetLineAvailableForActualToday() {
        BudgetLine line = line(230_000, 100_000, 80_000);

        assertThat(FinanceBudgetMath.availableAmount(line.getPlannedAmount(), line.getActualAmount(), line.getCommittedAmount()))
                .isEqualTo(line.getAvailableForActual());
    }

    @Test
    void remainingBudget_subtractsApprovedAndCommitted() {
        assertThat(FinanceBudgetMath.remainingBudget(230_000, 100_000, 80_000)).isEqualTo(50_000);
    }

    @Test
    void riskAmount_includesCommittedAndPending() {
        assertThat(FinanceBudgetMath.riskAmount(230_000, 100_000, 50_000, 80_000)).isEqualTo(0);
        assertThat(FinanceBudgetMath.riskAmount(230_000, 200_000, 50_000, 0)).isEqualTo(20_000);
    }

    @Test
    void approvedPolicyFlagsDocumentTargetModel() {
        assertThat(FinanceUpgradePolicy.UNIFIED_AVAILABLE_FORMULA).isTrue();
        assertThat(FinanceUpgradePolicy.PROCUREMENT_COMMIT_ON_ALLOCATE).isTrue();
        assertThat(FinanceUpgradePolicy.DIRECT_ACTUAL_COST_COMMIT_ON_CREATE).isTrue();
        assertThat(FinanceUpgradePolicy.PROCUREMENT_RECEIPT_COMMIT_ON_CREATE).isFalse();
        assertThat(FinanceUpgradePolicy.RELEASE_COMMITMENT_ON_ACTUAL_REJECT).isTrue();
        assertThat(FinanceUpgradePolicy.DEPARTMENT_SCOPED_REVIEW_QUEUE).isTrue();
        assertThat(FinanceUpgradePolicy.ACTUAL_METRIC_APPROVED_ONLY).isTrue();
    }

    private static BudgetLine line(double planned, double actual, double committed) {
        BudgetLine line = new BudgetLine();
        line.setPlannedAmount(planned);
        line.setActualAmount(actual);
        line.setCommittedAmount(committed);
        return line;
    }
}
