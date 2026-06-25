ALTER TABLE audit_logs
    ADD COLUMN IF NOT EXISTS reason text,
    ADD COLUMN IF NOT EXISTS source varchar(255),
    ADD COLUMN IF NOT EXISTS request_method varchar(32),
    ADD COLUMN IF NOT EXISTS request_path text,
    ADD COLUMN IF NOT EXISTS correlation_id varchar(255);

CREATE INDEX IF NOT EXISTS idx_audit_logs_module
    ON audit_logs (module);

CREATE INDEX IF NOT EXISTS idx_audit_logs_entity
    ON audit_logs (entity_type, entity_id);

CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at
    ON audit_logs (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_logs_correlation_id
    ON audit_logs (correlation_id);
