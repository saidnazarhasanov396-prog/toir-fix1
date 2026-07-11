CREATE UNIQUE INDEX uq_planned_shutdown_isolation_active_order
    ON planned_shutdown_isolation_points (planned_shutdown_id, order_number)
    WHERE is_deleted = false;

CREATE TABLE planned_shutdown_startup_tests (
    id uuid PRIMARY KEY,
    planned_shutdown_id uuid NOT NULL,
    test_key varchar(128) NOT NULL,
    title varchar(500) NOT NULL,
    mandatory boolean NOT NULL DEFAULT true,
    acceptance_criteria text NOT NULL,
    unit varchar(64),
    order_number integer NOT NULL DEFAULT 0,
    status varchar(32) NOT NULL DEFAULT 'PENDING',
    measured_value numeric(19,4),
    result_unit varchar(64),
    evidence text,
    performed_by_id uuid,
    verified_by_id uuid,
    verified_at timestamptz,
    is_deleted boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_planned_shutdown_startup_tests_shutdown
        FOREIGN KEY (planned_shutdown_id) REFERENCES planned_shutdowns(id),
    CONSTRAINT fk_planned_shutdown_startup_tests_performer
        FOREIGN KEY (performed_by_id) REFERENCES hr_employees(id),
    CONSTRAINT fk_planned_shutdown_startup_tests_verifier
        FOREIGN KEY (verified_by_id) REFERENCES hr_employees(id),
    CONSTRAINT chk_planned_shutdown_startup_tests_status
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'PASSED', 'FAILED', 'WAIVED')),
    CONSTRAINT chk_planned_shutdown_startup_tests_order CHECK (order_number >= 0),
    CONSTRAINT chk_planned_shutdown_startup_tests_result CHECK (
        (status IN ('PENDING', 'IN_PROGRESS') AND measured_value IS NULL AND evidence IS NULL
            AND performed_by_id IS NULL AND verified_by_id IS NULL AND verified_at IS NULL)
        OR (status IN ('PASSED', 'FAILED') AND measured_value IS NOT NULL AND evidence IS NOT NULL
            AND performed_by_id IS NOT NULL AND verified_by_id IS NOT NULL AND verified_at IS NOT NULL
            AND performed_by_id <> verified_by_id)
    )
);

CREATE UNIQUE INDEX uq_planned_shutdown_startup_tests_active_key
    ON planned_shutdown_startup_tests (planned_shutdown_id, test_key)
    WHERE is_deleted = false;

CREATE INDEX idx_planned_shutdown_startup_tests_status
    ON planned_shutdown_startup_tests (planned_shutdown_id, mandatory, status)
    WHERE is_deleted = false;

CREATE TABLE planned_shutdown_production_returns (
    id uuid PRIMARY KEY,
    planned_shutdown_id uuid NOT NULL,
    scope_version bigint NOT NULL,
    window_version bigint NOT NULL,
    approved_by_id uuid NOT NULL,
    approved_at timestamptz NOT NULL,
    evidence text NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_planned_shutdown_production_returns_shutdown
        FOREIGN KEY (planned_shutdown_id) REFERENCES planned_shutdowns(id),
    CONSTRAINT fk_planned_shutdown_production_returns_approver
        FOREIGN KEY (approved_by_id) REFERENCES users(id)
);

CREATE UNIQUE INDEX uq_planned_shutdown_production_returns_active
    ON planned_shutdown_production_returns (planned_shutdown_id)
    WHERE is_deleted = false;

CREATE TABLE planned_shutdown_closure_snapshots (
    id uuid PRIMARY KEY,
    planned_shutdown_id uuid NOT NULL,
    scope_version bigint NOT NULL,
    window_version bigint NOT NULL,
    closed_by_id uuid NOT NULL,
    closed_at timestamptz NOT NULL,
    snapshot_hash varchar(64) NOT NULL,
    snapshot_json text NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_planned_shutdown_closure_snapshots_shutdown
        FOREIGN KEY (planned_shutdown_id) REFERENCES planned_shutdowns(id),
    CONSTRAINT fk_planned_shutdown_closure_snapshots_actor
        FOREIGN KEY (closed_by_id) REFERENCES users(id),
    CONSTRAINT chk_planned_shutdown_closure_snapshots_hash
        CHECK (snapshot_hash ~ '^[0-9a-f]{64}$')
);

CREATE UNIQUE INDEX uq_planned_shutdown_closure_snapshots_active
    ON planned_shutdown_closure_snapshots (planned_shutdown_id)
    WHERE is_deleted = false;
