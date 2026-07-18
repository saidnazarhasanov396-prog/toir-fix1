CREATE TABLE erp_employee_clearance_outbox (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    employee_id uuid NOT NULL,
    module_code varchar(40) NOT NULL,
    source_revision bigint NOT NULL,
    cleared boolean NOT NULL,
    evidence_reference varchar(240),
    idempotency_key varchar(180) NOT NULL UNIQUE,
    payload text NOT NULL,
    status varchar(20) NOT NULL,
    attempts integer NOT NULL DEFAULT 0,
    max_attempts integer NOT NULL DEFAULT 8,
    next_attempt_at timestamptz,
    sent_at timestamptz,
    last_error text
);
CREATE INDEX idx_erp_employee_clearance_outbox_ready
    ON erp_employee_clearance_outbox (status, next_attempt_at, created_at);
