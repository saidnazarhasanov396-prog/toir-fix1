-- Finance module data audit (read-only). Run on staging/production before upgrade phases.
-- Generated for finance upgrade FAZA 0 (2026-07-03).

-- 1) Actual costs by status
SELECT status, count(*) AS cnt, round(sum(amount)::numeric, 2) AS total_amount
FROM actual_costs
WHERE is_deleted = false
GROUP BY status
ORDER BY status;

-- 2) Pending without budget line (approve guard will block after FAZA 6)
SELECT count(*) AS pending_unallocated_cnt,
       round(coalesce(sum(amount), 0)::numeric, 2) AS pending_unallocated_amount
FROM actual_costs
WHERE is_deleted = false
  AND status = 'PENDING'
  AND budget_line_id IS NULL;

-- 3) Category mismatch between actual cost and budget line
SELECT count(*) AS category_mismatch_cnt,
       round(coalesce(sum(ac.amount), 0)::numeric, 2) AS mismatch_amount
FROM actual_costs ac
JOIN budget_lines bl ON bl.id = ac.budget_line_id AND bl.is_deleted = false
WHERE ac.is_deleted = false
  AND ac.cost_category_id IS NOT NULL
  AND bl.cost_category_id IS NOT NULL
  AND ac.cost_category_id <> bl.cost_category_id;

-- 4) Budget lines over capacity (actual + committed > planned)
SELECT count(*) AS over_capacity_lines
FROM budget_lines
WHERE is_deleted = false
  AND (actual_amount + committed_amount) > planned_amount + 0.01;

-- 5) Committed exceeds planned
SELECT count(*) AS committed_overshoot_lines
FROM budget_lines
WHERE is_deleted = false
  AND committed_amount > planned_amount + 0.01;

-- 6) ALLOCATED procurement still SUBMITTED (commit-on-allocate not applied yet)
SELECT pr.id,
       pr.status,
       pr.budget_allocation_status,
       pr.total_estimated_cost,
       bl.committed_amount
FROM procurement_requests pr
JOIN budget_lines bl ON bl.id = pr.budget_line_id AND bl.is_deleted = false
WHERE pr.is_deleted = false
  AND pr.budget_allocation_status = 'ALLOCATED'
  AND pr.status = 'SUBMITTED';

-- 7) APPROVED procurement without matching committed (data drift)
SELECT pr.id,
       pr.total_estimated_cost,
       bl.committed_amount
FROM procurement_requests pr
JOIN budget_lines bl ON bl.id = pr.budget_line_id AND bl.is_deleted = false
WHERE pr.is_deleted = false
  AND pr.status = 'APPROVED'
  AND bl.committed_amount + 0.01 < pr.total_estimated_cost;

-- 8) PROCUREMENT_RECEIPT pending/rejected with positive committed on same line (stuck commit signal)
SELECT ac.id,
       ac.status,
       ac.amount,
       bl.committed_amount
FROM actual_costs ac
JOIN budget_lines bl ON bl.id = ac.budget_line_id AND bl.is_deleted = false
WHERE ac.is_deleted = false
  AND ac.source_type = 'PROCUREMENT_RECEIPT'
  AND ac.status IN ('PENDING', 'REJECTED')
  AND bl.committed_amount > 0;
