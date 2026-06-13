ALTER TABLE repair_requests
    ADD COLUMN IF NOT EXISTS template_id uuid;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_repair_requests_template'
    ) THEN
        ALTER TABLE repair_requests
            ADD CONSTRAINT fk_repair_requests_template
                FOREIGN KEY (template_id) REFERENCES maintenance_templates (id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_repair_requests_template
    ON repair_requests (template_id)
    WHERE is_deleted = false;
