package com.toir.finance;

/**
 * Canonical budget arithmetic for the finance module upgrade (approved 2026-07-03).
 * <p>
 * All dashboards, reports, and commitment guards should converge on these helpers.
 */
public final class FinanceBudgetMath {

    private FinanceBudgetMath() {
    }

    /**
     * Free budget on a line or aggregate: planned minus recognized actual spend minus active commitments.
     */
    public static double availableAmount(double plannedAmount, double actualAmount, double committedAmount) {
        return Math.max(0, plannedAmount - actualAmount - committedAmount);
    }

    /**
     * Remaining / available for reporting after approved actual costs and outstanding commitments.
     */
    public static double remainingBudget(double plannedAmount, double approvedActualAmount, double committedAmount) {
        return plannedAmount - approvedActualAmount - committedAmount;
    }

    /**
     * Risk / overspend exposure including pending review amounts not yet committed elsewhere.
     */
    public static double riskAmount(double plannedAmount,
                                    double approvedActualAmount,
                                    double pendingActualAmount,
                                    double committedAmount) {
        return Math.max(approvedActualAmount + pendingActualAmount + committedAmount - plannedAmount, 0);
    }

    public static double variance(double plannedAmount, double approvedActualAmount) {
        return plannedAmount - approvedActualAmount;
    }

    public static double burnRate(double plannedAmount, double approvedActualAmount) {
        return plannedAmount > 0 ? approvedActualAmount / plannedAmount : 0;
    }
}
