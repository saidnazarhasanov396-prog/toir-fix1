ALTER TABLE operational_issues
    ADD COLUMN IF NOT EXISTS metadata jsonb;

CREATE INDEX IF NOT EXISTS idx_operational_issues_low_stock_open
    ON operational_issues (source_type, source_id)
    WHERE is_deleted = false
      AND status = 'OPEN'
      AND source_type = 'LOW_STOCK';
