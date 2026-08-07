CREATE TABLE maintenance_action_embedding_jobs (
    id uuid PRIMARY KEY,
    maintenance_action_id uuid NOT NULL,
    source_schema_version varchar(64) NOT NULL,
    source_text_hash char(64) NOT NULL,
    normalized_source_text text NOT NULL,
    model_name varchar(255) NOT NULL,
    model_revision varchar(255) NOT NULL,
    dimension integer NOT NULL,
    status varchar(32) NOT NULL,
    is_current boolean NOT NULL DEFAULT true,
    attempt_count integer NOT NULL DEFAULT 0,
    maximum_attempts integer NOT NULL,
    next_attempt_at timestamptz,
    lease_owner varchar(255),
    lease_token uuid,
    lease_until timestamptz,
    safe_error_code varchar(128),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_maintenance_action_embedding_job_action
        FOREIGN KEY (maintenance_action_id) REFERENCES maintenance_actions(id),
    CONSTRAINT ck_maintenance_action_embedding_job_hash
        CHECK (source_text_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_maintenance_action_embedding_job_dimension
        CHECK (dimension > 0),
    CONSTRAINT ck_maintenance_action_embedding_job_attempts
        CHECK (attempt_count >= 0 AND maximum_attempts > 0 AND attempt_count <= maximum_attempts),
    CONSTRAINT ck_maintenance_action_embedding_job_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'RETRY_WAIT', 'READY', 'FAILED', 'STALE', 'SKIPPED')),
    CONSTRAINT ck_maintenance_action_embedding_job_lease
        CHECK ((status = 'PROCESSING' AND lease_owner IS NOT NULL AND lease_token IS NOT NULL AND lease_until IS NOT NULL)
            OR (status <> 'PROCESSING' AND lease_owner IS NULL AND lease_token IS NULL AND lease_until IS NULL)),
    CONSTRAINT ck_maintenance_action_embedding_job_ready_guard
        CHECK (status <> 'READY')
);

CREATE UNIQUE INDEX uq_maintenance_action_embedding_job_representation
    ON maintenance_action_embedding_jobs (
        maintenance_action_id,
        source_schema_version,
        source_text_hash,
        model_name,
        model_revision,
        dimension
    );

CREATE UNIQUE INDEX uq_maintenance_action_embedding_job_current_model
    ON maintenance_action_embedding_jobs (
        maintenance_action_id,
        source_schema_version,
        model_name,
        model_revision,
        dimension
    )
    WHERE is_current;

CREATE INDEX idx_maintenance_action_embedding_job_claim
    ON maintenance_action_embedding_jobs (next_attempt_at, created_at, id)
    WHERE is_current AND status IN ('PENDING', 'RETRY_WAIT', 'PROCESSING');

CREATE TABLE maintenance_action_embedding_backfill_runs (
    id uuid PRIMARY KEY,
    idempotency_key varchar(255) NOT NULL,
    model_name varchar(255) NOT NULL,
    model_revision varchar(255) NOT NULL,
    dimension integer NOT NULL,
    source_schema_version varchar(64) NOT NULL,
    batch_size integer NOT NULL,
    requested_by uuid,
    status varchar(32) NOT NULL,
    cursor uuid,
    scanned bigint NOT NULL DEFAULT 0,
    already_present bigint NOT NULL DEFAULT 0,
    enqueued bigint NOT NULL DEFAULT 0,
    skipped bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_maintenance_action_embedding_backfill_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_maintenance_action_embedding_backfill_dimension CHECK (dimension > 0),
    CONSTRAINT ck_maintenance_action_embedding_backfill_batch CHECK (batch_size > 0),
    CONSTRAINT ck_maintenance_action_embedding_backfill_counters
        CHECK (scanned >= 0 AND already_present >= 0 AND enqueued >= 0 AND skipped >= 0),
    CONSTRAINT ck_maintenance_action_embedding_backfill_status
        CHECK (status IN ('REQUESTED', 'RUNNING', 'PAUSED', 'SCAN_COMPLETED', 'COMPLETED', 'FAILED', 'CANCELLED'))
);

CREATE INDEX idx_maintenance_action_embedding_backfill_status
    ON maintenance_action_embedding_backfill_runs (status, updated_at, id);

COMMENT ON TABLE maintenance_action_embedding_jobs IS
    'Durable pre-pgvector orchestration only. Embedding vectors are intentionally not stored in this table.';
COMMENT ON CONSTRAINT ck_maintenance_action_embedding_job_ready_guard ON maintenance_action_embedding_jobs IS
    'Removed only by the future reviewed pgvector migration that adds atomic vector persistence and READY completion.';
