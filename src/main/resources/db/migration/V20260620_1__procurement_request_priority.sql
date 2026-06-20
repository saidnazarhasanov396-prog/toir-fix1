ALTER TABLE procurement_requests
    ADD COLUMN IF NOT EXISTS priority varchar(50) NOT NULL DEFAULT 'MEDIUM';

UPDATE procurement_requests
SET priority = 'MEDIUM'
WHERE priority IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'procurement_requests_priority_check'
    ) THEN
        ALTER TABLE procurement_requests
            ADD CONSTRAINT procurement_requests_priority_check
            CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL', 'EMERGENCY'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_procurement_requests_priority
    ON procurement_requests(priority);
