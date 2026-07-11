ALTER TABLE planned_shutdowns
    ADD COLUMN IF NOT EXISTS window_version bigint NOT NULL DEFAULT 1;

ALTER TABLE planned_shutdowns
    ADD CONSTRAINT chk_planned_shutdowns_window_version_positive
        CHECK (window_version > 0);

CREATE TABLE planned_shutdown_generation_requests (
    id uuid PRIMARY KEY,
    planned_shutdown_id uuid NOT NULL,
    idempotency_key varchar(255) NOT NULL,
    request_fingerprint varchar(64) NOT NULL,
    window_version bigint NOT NULL,
    ordered_work_order_ids text NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    CONSTRAINT fk_ps_generation_request_shutdown FOREIGN KEY (planned_shutdown_id)
        REFERENCES planned_shutdowns(id),
    CONSTRAINT uq_ps_generation_request_shutdown_key UNIQUE (planned_shutdown_id, idempotency_key),
    CONSTRAINT chk_ps_generation_request_window_positive CHECK (window_version > 0)
);

CREATE INDEX idx_ps_generation_request_shutdown
    ON planned_shutdown_generation_requests(planned_shutdown_id);
