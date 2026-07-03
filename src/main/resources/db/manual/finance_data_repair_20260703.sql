-- Finance module data repair (staging only — review audit output before running).
-- FAZA 0 manual script; NOT a Flyway migration until audit is signed off.

BEGIN;

-- A) Align actual cost category with budget line category when line is set
UPDATE actual_costs ac
SET cost_category_id = bl.cost_category_id,
    updated_at = NOW()
FROM budget_lines bl
WHERE ac.budget_line_id = bl.id
  AND ac.is_deleted = false
  AND bl.is_deleted = false
  AND ac.cost_category_id IS DISTINCT FROM bl.cost_category_id;

-- B) Cap committed_amount at planned_amount on lines (matches V20260703_1 intent)
UPDATE budget_lines
SET committed_amount = planned_amount,
    updated_at = NOW()
WHERE is_deleted = false
  AND committed_amount > planned_amount;

-- C) Soft-delete orphan pending actual costs without any source link (optional — comment out if unsure)
-- UPDATE actual_costs
-- SET is_deleted = true,
--     updated_at = NOW()
-- WHERE is_deleted = false
--   AND status = 'PENDING'
--   AND budget_line_id IS NULL
--   AND work_order_id IS NULL
--   AND repair_request_id IS NULL
--   AND contractor_work_id IS NULL
--   AND source_id IS NULL;

COMMIT;

-- Re-run finance_data_audit_20260703.sql after repair.
