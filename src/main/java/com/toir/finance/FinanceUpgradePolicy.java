package com.toir.finance;

/**
 * Approved finance-module policy decisions (2026-07-03). Reference for services, tests, and migrations.
 */
public final class FinanceUpgradePolicy {

    private FinanceUpgradePolicy() {
    }

    /** Available = planned - actual - committed everywhere in UI and APIs. */
    public static final boolean UNIFIED_AVAILABLE_FORMULA = true;

    /**
     * Procurement: commit on finance allocate; PR approve must not commit again.
     * Formal spend is recorded when receipt actual cost is approved after warehouse receipt.
     */
    public static final boolean PROCUREMENT_COMMIT_ON_ALLOCATE = true;

    /** Work-order and contractor actual costs commit on create when budget line is set. */
    public static final boolean DIRECT_ACTUAL_COST_COMMIT_ON_CREATE = true;

    /** Procurement receipt actual costs do not commit on create (PR already committed). */
    public static final boolean PROCUREMENT_RECEIPT_COMMIT_ON_CREATE = false;

    /** Reject / correction releases procurement commitments. */
    public static final boolean RELEASE_COMMITMENT_ON_ACTUAL_REJECT = true;

    /** Actual cost approve requires budgetLineId. */
    public static final boolean REQUIRE_BUDGET_LINE_ON_APPROVE = true;

    /** Receipt actual cost category follows budget line category. */
    public static final boolean RECEIPT_CATEGORY_FOLLOWS_BUDGET_LINE = true;

    /** Review queue and register use department scope; scope admin sees all. */
    public static final boolean DEPARTMENT_SCOPED_REVIEW_QUEUE = true;

    /** Summary actual metrics count APPROVED only; pending is a separate column. */
    public static final boolean ACTUAL_METRIC_APPROVED_ONLY = true;

    /** Prefer "available" naming over legacy "totalRemaining = planned - committed". */
    public static final boolean PREFER_AVAILABLE_NAMING = true;

    /**
     * Work-order material usage does not create WO actual costs — procurement receipt finance owns
     * spare-part / equipment purchase spend. WO finance covers labor and contractor only.
     */
    public static final boolean WORK_ORDER_EXCLUDES_PROCUREMENT_MATERIAL_COSTS = true;

    /**
     * Budget plan revise is allowed for responsible finance users on APPROVED and LOCKED budgets
     * (optional absorb of unplanned costs; not automatic).
     */
    public static final boolean BUDGET_REVISE_ALLOWED_WHEN_LOCKED = true;
}
