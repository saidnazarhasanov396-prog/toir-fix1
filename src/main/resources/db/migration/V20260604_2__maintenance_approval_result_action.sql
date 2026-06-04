ALTER TABLE maintenance_regulations
    ADD COLUMN IF NOT EXISTS approval_result_action VARCHAR(32) NOT NULL DEFAULT 'CREATE_TASK';
