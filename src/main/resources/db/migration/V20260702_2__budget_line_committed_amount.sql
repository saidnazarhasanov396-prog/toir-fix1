ALTER TABLE budget_lines
    ADD COLUMN IF NOT EXISTS committed_amount double precision NOT NULL DEFAULT 0.0;

CREATE INDEX IF NOT EXISTS idx_budget_lines_committed
    ON budget_lines (committed_amount)
    WHERE is_deleted = false;
