-- V4's closure shape cannot be upgraded truthfully: it has no canonical JSON/hash/window fact.
-- Task 7 had no producer before V10, so fail before any transformation unless it is empty.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM planned_shutdown_closure_snapshots) THEN
        RAISE EXCEPTION 'PLANNED_SHUTDOWN_CLOSURE_LEGACY_ROWS_REQUIRE_MANUAL_REMEDIATION';
    END IF;
END $$;

CREATE UNIQUE INDEX uq_planned_shutdown_isolation_active_order
    ON planned_shutdown_isolation_points (planned_shutdown_id, order_number)
    WHERE is_deleted = false;

-- V4 already created startup tests. Transform only evidence that can be preserved exactly.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM planned_shutdown_startup_tests WHERE status = 'WAIVED'
    ) THEN
        RAISE EXCEPTION 'PLANNED_SHUTDOWN_STARTUP_TEST_WAIVED_ROWS_REQUIRE_MANUAL_REMEDIATION';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM planned_shutdown_startup_tests
        WHERE btrim(test_code) = ''
           OR btrim(title) = ''
           OR btrim(acceptance_criteria) = ''
           OR (status IN ('PENDING', 'IN_PROGRESS') AND (
                measured_value IS NOT NULL OR measured_unit IS NOT NULL OR result_notes IS NOT NULL
                OR performed_by_id IS NOT NULL OR verified_by_id IS NOT NULL OR tested_at IS NOT NULL
           ))
           OR (status IN ('PASSED', 'FAILED') AND (
                measured_value IS NULL
                OR measured_value !~ '^[+-]?[0-9]{1,15}([.][0-9]{1,4})?$'
                OR nullif(btrim(measured_unit), '') IS NULL
                OR nullif(btrim(result_notes), '') IS NULL
                OR performed_by_id IS NULL OR verified_by_id IS NULL OR tested_at IS NULL
                OR performed_by_id = verified_by_id
           ))
    ) THEN
        RAISE EXCEPTION 'PLANNED_SHUTDOWN_STARTUP_TEST_ROWS_REQUIRE_MANUAL_REMEDIATION';
    END IF;
END $$;

DROP INDEX IF EXISTS uq_planned_shutdown_startup_tests_active_code;
DROP INDEX IF EXISTS idx_planned_shutdown_startup_tests_shutdown_status;

ALTER TABLE planned_shutdown_startup_tests
    DROP CONSTRAINT IF EXISTS fk_planned_shutdown_startup_tests_performed_by,
    DROP CONSTRAINT IF EXISTS fk_planned_shutdown_startup_tests_verified_by,
    DROP CONSTRAINT IF EXISTS chk_planned_shutdown_startup_tests_status,
    DROP CONSTRAINT IF EXISTS chk_planned_shutdown_startup_tests_order;

ALTER TABLE planned_shutdown_startup_tests RENAME COLUMN test_code TO test_key;
ALTER TABLE planned_shutdown_startup_tests RENAME COLUMN measured_unit TO result_unit;
ALTER TABLE planned_shutdown_startup_tests RENAME COLUMN result_notes TO evidence;
ALTER TABLE planned_shutdown_startup_tests RENAME COLUMN tested_at TO verified_at;

ALTER TABLE planned_shutdown_startup_tests
    ADD COLUMN unit varchar(64),
    ALTER COLUMN measured_value TYPE numeric(19,4)
        USING measured_value::numeric(19,4),
    ADD CONSTRAINT fk_planned_shutdown_startup_tests_performer
        FOREIGN KEY (performed_by_id) REFERENCES hr_employees(id),
    ADD CONSTRAINT fk_planned_shutdown_startup_tests_verifier
        FOREIGN KEY (verified_by_id) REFERENCES hr_employees(id),
    ADD CONSTRAINT chk_planned_shutdown_startup_tests_status
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'PASSED', 'FAILED')),
    ADD CONSTRAINT chk_planned_shutdown_startup_tests_order CHECK (order_number >= 0),
    ADD CONSTRAINT chk_planned_shutdown_startup_tests_result CHECK (
        (status IN ('PENDING', 'IN_PROGRESS') AND measured_value IS NULL AND result_unit IS NULL
            AND evidence IS NULL AND performed_by_id IS NULL AND verified_by_id IS NULL
            AND verified_at IS NULL)
        OR (status IN ('PASSED', 'FAILED') AND measured_value IS NOT NULL
            AND nullif(btrim(result_unit), '') IS NOT NULL
            AND nullif(btrim(evidence), '') IS NOT NULL
            AND performed_by_id IS NOT NULL AND verified_by_id IS NOT NULL
            AND verified_at IS NOT NULL AND performed_by_id <> verified_by_id)
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

DROP INDEX IF EXISTS uq_planned_shutdown_closure_active_shutdown;

ALTER TABLE planned_shutdown_closure_snapshots
    DROP CONSTRAINT IF EXISTS fk_planned_shutdown_closure_production_signoff,
    DROP CONSTRAINT IF EXISTS fk_planned_shutdown_closure_closed_by,
    DROP CONSTRAINT IF EXISTS chk_planned_shutdown_closure_version,
    DROP CONSTRAINT IF EXISTS chk_planned_shutdown_closure_downtime,
    DROP COLUMN closure_version,
    DROP COLUMN planned_downtime_minutes,
    DROP COLUMN actual_downtime_minutes,
    DROP COLUMN snapshot,
    DROP COLUMN production_signoff_employee_id,
    ADD COLUMN window_version bigint NOT NULL,
    ADD COLUMN snapshot_hash varchar(64) NOT NULL,
    ADD COLUMN snapshot_json text NOT NULL,
    ADD CONSTRAINT fk_planned_shutdown_closure_snapshots_actor
        FOREIGN KEY (closed_by_id) REFERENCES users(id),
    ADD CONSTRAINT chk_planned_shutdown_closure_snapshots_hash
        CHECK (snapshot_hash ~ '^[0-9a-f]{64}$');

CREATE UNIQUE INDEX uq_planned_shutdown_closure_snapshots_active
    ON planned_shutdown_closure_snapshots (planned_shutdown_id)
    WHERE is_deleted = false;
