ALTER TABLE maintenance_due_events
    ADD COLUMN IF NOT EXISTS reason_code varchar(64),
    ADD COLUMN IF NOT EXISTS reason_params jsonb;
