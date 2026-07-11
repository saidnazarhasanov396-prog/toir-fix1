ALTER TABLE planned_shutdowns
    DROP CONSTRAINT IF EXISTS planned_shutdowns_status_check;

ALTER TABLE planned_shutdowns
    ADD COLUMN code varchar(64),
    ADD COLUMN shutdown_type varchar(64) NOT NULL DEFAULT 'PLANNED',
    ADD COLUMN responsible_employee_id uuid,
    ADD COLUMN objective text,
    ADD COLUMN notes text,
    ADD COLUMN risk_level varchar(32),
    ADD COLUMN risk_score numeric(9,4),
    ADD COLUMN approval_scope_version bigint,
    ADD COLUMN approval_scope_hash varchar(128),
    ADD COLUMN planned_start_at timestamptz,
    ADD COLUMN planned_end_at timestamptz,
    ADD COLUMN approved_start_at timestamptz,
    ADD COLUMN approved_end_at timestamptz,
    ADD COLUMN effective_extension_end_at timestamptz,
    ADD COLUMN actual_shutdown_at timestamptz,
    ADD COLUMN actual_safe_state_at timestamptz,
    ADD COLUMN actual_repair_start_at timestamptz,
    ADD COLUMN actual_testing_start_at timestamptz,
    ADD COLUMN actual_startup_at timestamptz,
    ADD COLUMN actual_completed_at timestamptz,
    ADD COLUMN reschedule_reason text,
    ADD COLUMN extension_reason text,
    ADD COLUMN closure_version bigint NOT NULL DEFAULT 0;

-- Legacy rows have no scope, safety, approval-snapshot, isolation, or actual-time evidence.
-- Preserve their requested window but keep formerly actionable states outside the new startable states.
UPDATE planned_shutdowns
SET code = 'PS-LEGACY-' || upper(replace(id::text, '-', '')),
    planned_start_at = start_at,
    planned_end_at = end_at,
    status = CASE lower(status)
        WHEN 'draft' THEN 'DRAFT'
        WHEN 'generated' THEN 'SCOPE_FORMATION'
        WHEN 'approved' THEN 'PENDING_APPROVAL'
        WHEN 'in_progress' THEN 'READINESS_CHECK'
        WHEN 'closed' THEN 'CLOSED'
        WHEN 'cancelled' THEN 'CANCELLED'
        ELSE 'DRAFT'
    END;

ALTER TABLE planned_shutdowns
    ALTER COLUMN code SET NOT NULL,
    ALTER COLUMN planned_start_at SET NOT NULL,
    ALTER COLUMN planned_end_at SET NOT NULL,
    ADD CONSTRAINT fk_planned_shutdowns_responsible_employee
        FOREIGN KEY (responsible_employee_id) REFERENCES hr_employees(id),
    ADD CONSTRAINT chk_planned_shutdown_status CHECK (status IN (
        'DRAFT', 'SCOPE_FORMATION', 'READINESS_CHECK', 'PENDING_APPROVAL', 'APPROVED',
        'PREPARATION', 'SHUTDOWN_STARTED', 'SAFE_STATE', 'REPAIR_IN_PROGRESS', 'TESTING',
        'STARTUP', 'COMPLETED', 'CLOSED', 'CANCELLED', 'RESCHEDULED', 'EMERGENCY_EXTENDED'
    )),
    ADD CONSTRAINT chk_planned_shutdown_window CHECK (planned_end_at > planned_start_at) NOT VALID,
    ADD CONSTRAINT chk_planned_shutdown_approved_window CHECK (
        (approved_start_at IS NULL AND approved_end_at IS NULL)
        OR (approved_start_at IS NOT NULL AND approved_end_at IS NOT NULL
            AND approved_end_at > approved_start_at)
    ),
    ADD CONSTRAINT chk_planned_shutdown_extension_window CHECK (
        effective_extension_end_at IS NULL
        OR approved_end_at IS NOT NULL AND effective_extension_end_at > approved_end_at
    ),
    ADD CONSTRAINT chk_planned_shutdown_risk_score CHECK (risk_score IS NULL OR risk_score >= 0),
    ADD CONSTRAINT chk_planned_shutdown_scope_snapshot CHECK (
        (approval_scope_version IS NULL AND approval_scope_hash IS NULL)
        OR (approval_scope_version IS NOT NULL AND approval_scope_hash IS NOT NULL)
    );

CREATE UNIQUE INDEX uq_planned_shutdowns_active_code
    ON planned_shutdowns (code)
    WHERE is_deleted = false;

CREATE INDEX idx_planned_shutdowns_department_status
    ON planned_shutdowns (department_id, status)
    WHERE is_deleted = false;

CREATE TABLE planned_shutdown_assets (
    id uuid PRIMARY KEY,
    planned_shutdown_id uuid NOT NULL,
    equipment_id uuid NOT NULL,
    disposition varchar(32) NOT NULL,
    inclusion_reason text,
    order_number integer NOT NULL DEFAULT 0,
    is_deleted boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_planned_shutdown_assets_shutdown
        FOREIGN KEY (planned_shutdown_id) REFERENCES planned_shutdowns(id),
    CONSTRAINT fk_planned_shutdown_assets_equipment
        FOREIGN KEY (equipment_id) REFERENCES equipment(id),
    CONSTRAINT chk_planned_shutdown_assets_disposition
        CHECK (disposition IN ('STOPPED', 'RESERVE', 'RUNNING')),
    CONSTRAINT chk_planned_shutdown_assets_order CHECK (order_number >= 0)
);

CREATE UNIQUE INDEX uq_planned_shutdown_assets_active_equipment
    ON planned_shutdown_assets (planned_shutdown_id, equipment_id)
    WHERE is_deleted = false;

CREATE INDEX idx_planned_shutdown_assets_shutdown_order
    ON planned_shutdown_assets (planned_shutdown_id, order_number)
    WHERE is_deleted = false;

CREATE TABLE planned_shutdown_work_items (
    id uuid PRIMARY KEY,
    planned_shutdown_id uuid NOT NULL,
    source_type varchar(32) NOT NULL,
    source_id uuid,
    equipment_id uuid,
    title varchar(500) NOT NULL,
    priority varchar(32) NOT NULL DEFAULT 'MEDIUM',
    requires_shutdown boolean NOT NULL DEFAULT true,
    requires_isolation boolean NOT NULL DEFAULT false,
    planned_duration_minutes integer,
    criticality varchar(32),
    order_number integer NOT NULL DEFAULT 0,
    status varchar(32) NOT NULL DEFAULT 'PENDING',
    is_deleted boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_planned_shutdown_work_items_shutdown
        FOREIGN KEY (planned_shutdown_id) REFERENCES planned_shutdowns(id),
    CONSTRAINT fk_planned_shutdown_work_items_equipment
        FOREIGN KEY (equipment_id) REFERENCES equipment(id),
    CONSTRAINT chk_planned_shutdown_work_items_source_type
        CHECK (source_type IN ('MANUAL', 'DEFECT', 'PPR', 'WORK_ORDER', 'REPAIR_CAMPAIGN')),
    CONSTRAINT chk_planned_shutdown_work_items_source_identity CHECK (
        (source_type = 'MANUAL' AND source_id IS NULL)
        OR (source_type <> 'MANUAL' AND source_id IS NOT NULL)
    ),
    CONSTRAINT chk_planned_shutdown_work_items_status
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'PASSED', 'FAILED', 'WAIVED')),
    CONSTRAINT chk_planned_shutdown_work_items_duration
        CHECK (planned_duration_minutes IS NULL OR planned_duration_minutes > 0),
    CONSTRAINT chk_planned_shutdown_work_items_order CHECK (order_number >= 0)
);

CREATE UNIQUE INDEX uq_planned_shutdown_work_items_active_source
    ON planned_shutdown_work_items (planned_shutdown_id, source_type, source_id)
    WHERE is_deleted = false AND source_id IS NOT NULL;

CREATE UNIQUE INDEX uq_planned_shutdown_work_items_active_order
    ON planned_shutdown_work_items (planned_shutdown_id, order_number)
    WHERE is_deleted = false;

CREATE INDEX idx_planned_shutdown_work_items_equipment
    ON planned_shutdown_work_items (equipment_id)
    WHERE is_deleted = false;

CREATE TABLE planned_shutdown_readiness_items (
    id uuid PRIMARY KEY,
    planned_shutdown_id uuid NOT NULL,
    readiness_key varchar(128) NOT NULL,
    source_type varchar(64),
    source_id uuid,
    title varchar(500) NOT NULL,
    severity varchar(16) NOT NULL,
    status varchar(32) NOT NULL DEFAULT 'PENDING',
    responsible_employee_id uuid,
    due_at timestamptz,
    evidence text,
    comment text,
    completed_by_id uuid,
    completed_at timestamptz,
    order_number integer NOT NULL DEFAULT 0,
    is_deleted boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_planned_shutdown_readiness_items_shutdown
        FOREIGN KEY (planned_shutdown_id) REFERENCES planned_shutdowns(id),
    CONSTRAINT fk_planned_shutdown_readiness_responsible_employee
        FOREIGN KEY (responsible_employee_id) REFERENCES hr_employees(id),
    CONSTRAINT fk_planned_shutdown_readiness_completed_by
        FOREIGN KEY (completed_by_id) REFERENCES users(id),
    CONSTRAINT chk_planned_shutdown_readiness_severity
        CHECK (severity IN ('CRITICAL', 'WARNING')),
    CONSTRAINT chk_planned_shutdown_readiness_status
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'PASSED', 'FAILED', 'WAIVED')),
    CONSTRAINT chk_planned_shutdown_readiness_completion CHECK (
        (completed_at IS NULL AND completed_by_id IS NULL)
        OR (completed_at IS NOT NULL AND completed_by_id IS NOT NULL)
    ),
    CONSTRAINT chk_planned_shutdown_readiness_order CHECK (order_number >= 0)
);

CREATE UNIQUE INDEX uq_planned_shutdown_readiness_active_key
    ON planned_shutdown_readiness_items (planned_shutdown_id, readiness_key)
    WHERE is_deleted = false;

CREATE INDEX idx_planned_shutdown_readiness_shutdown_severity
    ON planned_shutdown_readiness_items (planned_shutdown_id, severity, status)
    WHERE is_deleted = false;

CREATE TABLE planned_shutdown_isolation_points (
    id uuid PRIMARY KEY,
    planned_shutdown_id uuid NOT NULL,
    equipment_id uuid NOT NULL,
    location_id uuid,
    isolation_method varchar(255) NOT NULL,
    lock_tag_identifier varchar(255) NOT NULL,
    responsible_employee_id uuid NOT NULL,
    status varchar(32) NOT NULL DEFAULT 'PENDING',
    permit_id uuid,
    applied_by_id uuid,
    applied_at timestamptz,
    verified_by_id uuid,
    verified_at timestamptz,
    released_by_id uuid,
    released_at timestamptz,
    order_number integer NOT NULL DEFAULT 0,
    is_deleted boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_planned_shutdown_isolation_points_shutdown
        FOREIGN KEY (planned_shutdown_id) REFERENCES planned_shutdowns(id),
    CONSTRAINT fk_planned_shutdown_isolation_equipment
        FOREIGN KEY (equipment_id) REFERENCES equipment(id),
    CONSTRAINT fk_planned_shutdown_isolation_location
        FOREIGN KEY (location_id) REFERENCES locations(id),
    CONSTRAINT fk_planned_shutdown_isolation_responsible_employee
        FOREIGN KEY (responsible_employee_id) REFERENCES hr_employees(id),
    CONSTRAINT fk_planned_shutdown_isolation_applied_by
        FOREIGN KEY (applied_by_id) REFERENCES hr_employees(id),
    CONSTRAINT fk_planned_shutdown_isolation_verified_by
        FOREIGN KEY (verified_by_id) REFERENCES hr_employees(id),
    CONSTRAINT fk_planned_shutdown_isolation_released_by
        FOREIGN KEY (released_by_id) REFERENCES hr_employees(id),
    CONSTRAINT fk_planned_shutdown_isolation_permit
        FOREIGN KEY (permit_id) REFERENCES safety_permits(id),
    CONSTRAINT chk_planned_shutdown_isolation_status
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'PASSED', 'FAILED', 'WAIVED')),
    CONSTRAINT chk_planned_shutdown_isolation_applied CHECK (
        (applied_at IS NULL AND applied_by_id IS NULL)
        OR (applied_at IS NOT NULL AND applied_by_id IS NOT NULL)
    ),
    CONSTRAINT chk_planned_shutdown_isolation_verified CHECK (
        (verified_at IS NULL AND verified_by_id IS NULL)
        OR (verified_at IS NOT NULL AND verified_by_id IS NOT NULL AND applied_at IS NOT NULL)
    ),
    CONSTRAINT chk_planned_shutdown_isolation_released CHECK (
        (released_at IS NULL AND released_by_id IS NULL)
        OR (released_at IS NOT NULL AND released_by_id IS NOT NULL AND verified_at IS NOT NULL)
    ),
    CONSTRAINT chk_planned_shutdown_isolation_order CHECK (order_number >= 0)
);

CREATE UNIQUE INDEX uq_planned_shutdown_isolation_active_lock_tag
    ON planned_shutdown_isolation_points (planned_shutdown_id, lock_tag_identifier)
    WHERE is_deleted = false;

CREATE INDEX idx_planned_shutdown_isolation_shutdown_status
    ON planned_shutdown_isolation_points (planned_shutdown_id, status)
    WHERE is_deleted = false;

CREATE TABLE planned_shutdown_status_history (
    id uuid PRIMARY KEY,
    planned_shutdown_id uuid NOT NULL,
    from_status varchar(32),
    to_status varchar(32) NOT NULL,
    actor_id uuid NOT NULL,
    reason text,
    old_effective_start_at timestamptz,
    old_effective_end_at timestamptz,
    new_effective_start_at timestamptz,
    new_effective_end_at timestamptz,
    scope_version bigint,
    correlation_key varchar(255),
    occurred_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_planned_shutdown_status_history_shutdown
        FOREIGN KEY (planned_shutdown_id) REFERENCES planned_shutdowns(id),
    CONSTRAINT fk_planned_shutdown_status_history_actor
        FOREIGN KEY (actor_id) REFERENCES users(id),
    CONSTRAINT chk_planned_shutdown_history_from_status CHECK (from_status IS NULL OR from_status IN (
        'DRAFT', 'SCOPE_FORMATION', 'READINESS_CHECK', 'PENDING_APPROVAL', 'APPROVED',
        'PREPARATION', 'SHUTDOWN_STARTED', 'SAFE_STATE', 'REPAIR_IN_PROGRESS', 'TESTING',
        'STARTUP', 'COMPLETED', 'CLOSED', 'CANCELLED', 'RESCHEDULED', 'EMERGENCY_EXTENDED'
    )),
    CONSTRAINT chk_planned_shutdown_history_to_status CHECK (to_status IN (
        'DRAFT', 'SCOPE_FORMATION', 'READINESS_CHECK', 'PENDING_APPROVAL', 'APPROVED',
        'PREPARATION', 'SHUTDOWN_STARTED', 'SAFE_STATE', 'REPAIR_IN_PROGRESS', 'TESTING',
        'STARTUP', 'COMPLETED', 'CLOSED', 'CANCELLED', 'RESCHEDULED', 'EMERGENCY_EXTENDED'
    ))
);

CREATE UNIQUE INDEX uq_planned_shutdown_history_correlation
    ON planned_shutdown_status_history (planned_shutdown_id, correlation_key)
    WHERE is_deleted = false AND correlation_key IS NOT NULL;

CREATE INDEX idx_planned_shutdown_history_timeline
    ON planned_shutdown_status_history (planned_shutdown_id, occurred_at);

CREATE TABLE planned_shutdown_startup_tests (
    id uuid PRIMARY KEY,
    planned_shutdown_id uuid NOT NULL,
    test_code varchar(128) NOT NULL,
    title varchar(500) NOT NULL,
    mandatory boolean NOT NULL DEFAULT true,
    acceptance_criteria text NOT NULL,
    measured_value varchar(255),
    measured_unit varchar(64),
    result_notes text,
    status varchar(32) NOT NULL DEFAULT 'PENDING',
    performed_by_id uuid,
    verified_by_id uuid,
    tested_at timestamptz,
    order_number integer NOT NULL DEFAULT 0,
    is_deleted boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_planned_shutdown_startup_tests_shutdown
        FOREIGN KEY (planned_shutdown_id) REFERENCES planned_shutdowns(id),
    CONSTRAINT fk_planned_shutdown_startup_tests_performed_by
        FOREIGN KEY (performed_by_id) REFERENCES hr_employees(id),
    CONSTRAINT fk_planned_shutdown_startup_tests_verified_by
        FOREIGN KEY (verified_by_id) REFERENCES hr_employees(id),
    CONSTRAINT chk_planned_shutdown_startup_tests_status
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'PASSED', 'FAILED', 'WAIVED')),
    CONSTRAINT chk_planned_shutdown_startup_tests_order CHECK (order_number >= 0)
);

CREATE UNIQUE INDEX uq_planned_shutdown_startup_tests_active_code
    ON planned_shutdown_startup_tests (planned_shutdown_id, test_code)
    WHERE is_deleted = false;

CREATE INDEX idx_planned_shutdown_startup_tests_shutdown_status
    ON planned_shutdown_startup_tests (planned_shutdown_id, status)
    WHERE is_deleted = false;

CREATE TABLE planned_shutdown_closure_snapshots (
    id uuid PRIMARY KEY,
    planned_shutdown_id uuid NOT NULL,
    closure_version bigint NOT NULL,
    scope_version bigint NOT NULL,
    planned_downtime_minutes bigint NOT NULL,
    actual_downtime_minutes bigint NOT NULL,
    snapshot jsonb NOT NULL,
    production_signoff_employee_id uuid NOT NULL,
    closed_by_id uuid NOT NULL,
    closed_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_planned_shutdown_closure_snapshots_shutdown
        FOREIGN KEY (planned_shutdown_id) REFERENCES planned_shutdowns(id),
    CONSTRAINT fk_planned_shutdown_closure_production_signoff
        FOREIGN KEY (production_signoff_employee_id) REFERENCES hr_employees(id),
    CONSTRAINT fk_planned_shutdown_closure_closed_by
        FOREIGN KEY (closed_by_id) REFERENCES users(id),
    CONSTRAINT chk_planned_shutdown_closure_version CHECK (closure_version > 0),
    CONSTRAINT chk_planned_shutdown_closure_downtime CHECK (
        planned_downtime_minutes >= 0 AND actual_downtime_minutes >= 0
    )
);

CREATE UNIQUE INDEX uq_planned_shutdown_closure_active_shutdown
    ON planned_shutdown_closure_snapshots (planned_shutdown_id)
    WHERE is_deleted = false;

ALTER TABLE work_orders
    ADD COLUMN planned_shutdown_id uuid,
    ADD COLUMN shutdown_work_item_id uuid,
    ADD CONSTRAINT fk_work_orders_planned_shutdown
        FOREIGN KEY (planned_shutdown_id) REFERENCES planned_shutdowns(id),
    ADD CONSTRAINT fk_work_orders_shutdown_work_item
        FOREIGN KEY (shutdown_work_item_id) REFERENCES planned_shutdown_work_items(id),
    ADD CONSTRAINT chk_work_orders_shutdown_link_pair CHECK (
        shutdown_work_item_id IS NULL OR planned_shutdown_id IS NOT NULL
    );

CREATE INDEX idx_work_orders_planned_shutdown_id
    ON work_orders (planned_shutdown_id)
    WHERE is_deleted = false;

CREATE INDEX idx_work_orders_shutdown_work_item_id
    ON work_orders (shutdown_work_item_id)
    WHERE is_deleted = false;
