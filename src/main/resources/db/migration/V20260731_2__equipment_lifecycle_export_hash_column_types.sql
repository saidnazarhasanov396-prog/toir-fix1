ALTER TABLE equipment_lifecycle_export_jobs
    ALTER COLUMN request_fingerprint TYPE VARCHAR(64)
    USING request_fingerprint::VARCHAR(64);

ALTER TABLE equipment_lifecycle_export_jobs
    ALTER COLUMN policy_fingerprint TYPE VARCHAR(64)
    USING policy_fingerprint::VARCHAR(64);

ALTER TABLE equipment_lifecycle_export_parts
    ALTER COLUMN sha256 TYPE VARCHAR(64)
    USING sha256::VARCHAR(64);

ALTER TABLE equipment_lifecycle_export_artifacts
    ALTER COLUMN sha256 TYPE VARCHAR(64)
    USING sha256::VARCHAR(64);
