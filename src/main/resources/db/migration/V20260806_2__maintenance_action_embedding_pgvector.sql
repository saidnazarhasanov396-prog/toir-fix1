CREATE TABLE maintenance_action_embeddings (
    job_id uuid PRIMARY KEY,
    maintenance_action_id uuid NOT NULL,
    source_schema_version varchar(64) NOT NULL,
    source_text_hash char(64) NOT NULL,
    model_name varchar(255) NOT NULL,
    model_revision varchar(255) NOT NULL,
    dimension integer NOT NULL,
    embedding vector(768) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_maintenance_action_embedding_job
        FOREIGN KEY (job_id) REFERENCES maintenance_action_embedding_jobs(id) ON DELETE CASCADE,
    CONSTRAINT fk_maintenance_action_embedding_action
        FOREIGN KEY (maintenance_action_id) REFERENCES maintenance_actions(id),
    CONSTRAINT ck_maintenance_action_embedding_hash
        CHECK (source_text_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_maintenance_action_embedding_dimension
        CHECK (dimension = 768),
    CONSTRAINT uq_maintenance_action_embedding_representation UNIQUE (
        maintenance_action_id,
        source_schema_version,
        source_text_hash,
        model_name,
        model_revision,
        dimension
    )
);

CREATE INDEX idx_maintenance_action_embedding_compatibility
    ON maintenance_action_embeddings (
        model_name,
        model_revision,
        dimension,
        source_schema_version,
        maintenance_action_id
    );

ALTER TABLE maintenance_action_embedding_jobs
    DROP CONSTRAINT ck_maintenance_action_embedding_job_ready_guard;

COMMENT ON COLUMN maintenance_action_embeddings.embedding IS
    'Real pgvector vector(768); exact cosine search is used initially without an ANN index.';
