CREATE TABLE equipment_lifecycle_export_jobs (
    id UUID PRIMARY KEY,
    creator_id UUID NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    request_snapshot JSONB NOT NULL,
    authorization_scope JSONB NOT NULL,
    status VARCHAR(32) NOT NULL,
    as_of TIMESTAMPTZ NOT NULL,
    schema_version VARCHAR(16) NOT NULL,
    manifest_version VARCHAR(16) NOT NULL,
    context_profile VARCHAR(80) NOT NULL,
    resolved_policy JSONB NOT NULL,
    policy_fingerprint CHAR(64) NOT NULL,
    selection_mode VARCHAR(32) NOT NULL,
    selection_frozen BOOLEAN NOT NULL DEFAULT FALSE,
    selected_count BIGINT NOT NULL DEFAULT 0,
    completed_count BIGINT NOT NULL DEFAULT 0,
    last_completed_ordinal BIGINT NOT NULL DEFAULT -1,
    lease_owner VARCHAR(160),
    lease_token UUID,
    lease_expires_at TIMESTAMPTZ,
    heartbeat_at TIMESTAMPTZ,
    failure_code VARCHAR(80),
    failure_summary VARCHAR(500),
    failure_equipment_id UUID,
    resume_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    started_at TIMESTAMPTZ,
    finalization_time TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    cancel_requested_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    cleanup_claim_token UUID,
    cleanup_claim_until TIMESTAMPTZ,
    staging_cleanup_complete BOOLEAN NOT NULL DEFAULT FALSE,
    cleanup_complete BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_equipment_lifecycle_export_creator_idempotency UNIQUE (creator_id, idempotency_key),
    CONSTRAINT ck_equipment_lifecycle_export_status CHECK (status IN (
        'QUEUED', 'PREPARING', 'RUNNING', 'FINALIZING', 'COMPLETED',
        'CANCEL_REQUESTED', 'CANCELLED', 'FAILED', 'EXPIRED'
    )),
    CONSTRAINT ck_equipment_lifecycle_export_selection_mode CHECK (selection_mode IN ('EXPLICIT_IDS', 'ALL_AUTHORIZED')),
    CONSTRAINT ck_equipment_lifecycle_export_selected_count CHECK (selected_count >= 0),
    CONSTRAINT ck_equipment_lifecycle_export_completed_count CHECK (completed_count >= 0),
    CONSTRAINT ck_equipment_lifecycle_export_last_ordinal CHECK (last_completed_ordinal >= -1),
    CONSTRAINT ck_equipment_lifecycle_export_progress CHECK (completed_count <= selected_count)
);

CREATE INDEX idx_equipment_lifecycle_export_jobs_status_created
    ON equipment_lifecycle_export_jobs (status, created_at DESC, id DESC);
CREATE INDEX idx_equipment_lifecycle_export_jobs_expiry_status
    ON equipment_lifecycle_export_jobs (expires_at, status) WHERE expires_at IS NOT NULL;
CREATE INDEX idx_equipment_lifecycle_export_jobs_lease_status
    ON equipment_lifecycle_export_jobs (lease_expires_at, status) WHERE lease_token IS NOT NULL;
CREATE INDEX idx_equipment_lifecycle_export_jobs_cleanup_claim
    ON equipment_lifecycle_export_jobs (cleanup_claim_until, status, updated_at);

CREATE TABLE equipment_lifecycle_export_membership (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES equipment_lifecycle_export_jobs(id),
    equipment_id UUID NOT NULL,
    ordinal BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_equipment_lifecycle_export_membership_equipment UNIQUE (job_id, equipment_id),
    CONSTRAINT uq_equipment_lifecycle_export_membership_ordinal UNIQUE (job_id, ordinal),
    CONSTRAINT ck_equipment_lifecycle_export_membership_ordinal CHECK (ordinal >= 0)
);

CREATE INDEX idx_equipment_lifecycle_export_membership_job_ordinal
    ON equipment_lifecycle_export_membership (job_id, ordinal);

CREATE TABLE equipment_lifecycle_export_parts (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES equipment_lifecycle_export_jobs(id),
    part_number INTEGER NOT NULL,
    first_ordinal BIGINT NOT NULL,
    last_ordinal BIGINT NOT NULL,
    record_count INTEGER NOT NULL,
    object_key TEXT NOT NULL,
    object_size BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    fencing_token UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_equipment_lifecycle_export_part_number UNIQUE (job_id, part_number),
    CONSTRAINT uq_equipment_lifecycle_export_part_first_ordinal UNIQUE (job_id, first_ordinal),
    CONSTRAINT ck_equipment_lifecycle_export_part_number CHECK (part_number >= 0),
    CONSTRAINT ck_equipment_lifecycle_export_part_ordinals CHECK (first_ordinal >= 0 AND last_ordinal >= first_ordinal),
    CONSTRAINT ck_equipment_lifecycle_export_part_count CHECK (record_count > 0 AND record_count = last_ordinal - first_ordinal + 1),
    CONSTRAINT ck_equipment_lifecycle_export_part_size CHECK (object_size >= 0)
);

CREATE INDEX idx_equipment_lifecycle_export_parts_job_order
    ON equipment_lifecycle_export_parts (job_id, part_number, first_ordinal);

CREATE TABLE equipment_lifecycle_export_artifacts (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES equipment_lifecycle_export_jobs(id),
    artifact_type VARCHAR(32) NOT NULL,
    filename VARCHAR(160) NOT NULL,
    media_type VARCHAR(120) NOT NULL,
    object_key TEXT NOT NULL,
    object_size BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_equipment_lifecycle_export_artifact_type UNIQUE (job_id, artifact_type),
    CONSTRAINT ck_equipment_lifecycle_export_artifact_type CHECK (artifact_type IN ('DATASET', 'SCHEMA', 'MANIFEST', 'CHECKSUMS')),
    CONSTRAINT ck_equipment_lifecycle_export_artifact_size CHECK (object_size >= 0)
);

CREATE INDEX idx_equipment_lifecycle_export_artifacts_job
    ON equipment_lifecycle_export_artifacts (job_id, artifact_type);
