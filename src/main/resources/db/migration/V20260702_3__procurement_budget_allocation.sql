ALTER TABLE procurement_requests
    ADD COLUMN IF NOT EXISTS budget_line_id uuid REFERENCES budget_lines (id),
    ADD COLUMN IF NOT EXISTS budget_allocation_status varchar(32) NOT NULL DEFAULT 'UNALLOCATED',
    ADD COLUMN IF NOT EXISTS budget_allocated_at timestamptz,
    ADD COLUMN IF NOT EXISTS budget_allocated_by_id uuid;

ALTER TABLE procurement_requests
    DROP CONSTRAINT IF EXISTS procurement_requests_allocation_status_check;

ALTER TABLE procurement_requests
    ADD CONSTRAINT procurement_requests_allocation_status_check
        CHECK (budget_allocation_status IN ('UNALLOCATED', 'ALLOCATED', 'REJECTED'));

CREATE INDEX IF NOT EXISTS idx_procurement_requests_budget_allocation
    ON procurement_requests (budget_allocation_status, budget_line_id)
    WHERE is_deleted = false;

UPDATE budget_lines bl
SET committed_amount = bl.committed_amount + (
    SELECT COALESCE(SUM(pr.total_estimated_cost), 0)
    FROM procurement_requests pr
    WHERE pr.budget_line_id = bl.id
      AND pr.status = 'APPROVED'
      AND pr.budget_allocation_status = 'ALLOCATED'
      AND pr.is_deleted = false
)
WHERE bl.id IN (
    SELECT DISTINCT budget_line_id
    FROM procurement_requests
    WHERE status = 'APPROVED'
      AND budget_allocation_status = 'ALLOCATED'
      AND budget_line_id IS NOT NULL
      AND is_deleted = false
);

UPDATE procurement_requests
SET budget_allocation_status = 'ALLOCATED',
    budget_allocated_at = approved_at,
    budget_allocated_by_id = approved_by
WHERE status = 'APPROVED'
  AND budget_line_id IS NOT NULL
  AND budget_allocation_status = 'UNALLOCATED'
  AND is_deleted = false;
