ALTER TABLE maintenance_completion_anchors
    ADD COLUMN IF NOT EXISTS repair_request_id uuid;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_maintenance_completion_anchors_repair_request'
    ) THEN
        ALTER TABLE maintenance_completion_anchors
            ADD CONSTRAINT fk_maintenance_completion_anchors_repair_request
                FOREIGN KEY (repair_request_id) REFERENCES repair_requests (id);
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_maintenance_completion_anchors_repair_request
    ON maintenance_completion_anchors (repair_request_id)
    WHERE repair_request_id IS NOT NULL AND is_deleted = false;
