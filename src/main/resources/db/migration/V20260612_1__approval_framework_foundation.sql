ALTER TABLE approval_requests
    ADD COLUMN IF NOT EXISTS target_type VARCHAR(64),
    ADD COLUMN IF NOT EXISTS target_id UUID,
    ADD COLUMN IF NOT EXISTS action_type VARCHAR(64),
    ADD COLUMN IF NOT EXISTS payload_json TEXT,
    ADD COLUMN IF NOT EXISTS result_json TEXT,
    ADD COLUMN IF NOT EXISTS failure_reason TEXT,
    ADD COLUMN IF NOT EXISTS version BIGINT;

UPDATE approval_requests
SET version = 0
WHERE version IS NULL;

ALTER TABLE approval_requests
    ALTER COLUMN version SET DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_approval_target
    ON approval_requests (target_type, target_id);

CREATE INDEX IF NOT EXISTS idx_approval_action
    ON approval_requests (action_type);
