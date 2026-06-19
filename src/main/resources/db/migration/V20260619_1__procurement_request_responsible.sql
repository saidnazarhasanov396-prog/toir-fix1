ALTER TABLE procurement_requests
    ADD COLUMN IF NOT EXISTS responsible_id uuid;

CREATE INDEX IF NOT EXISTS idx_procurement_requests_responsible
    ON procurement_requests (responsible_id)
    WHERE is_deleted = false AND responsible_id IS NOT NULL;
