ALTER TABLE audit_logs
    ADD COLUMN IF NOT EXISTS previous_snapshot jsonb,
    ADD COLUMN IF NOT EXISTS current_snapshot jsonb,
    ADD COLUMN IF NOT EXISTS diff_json jsonb;
