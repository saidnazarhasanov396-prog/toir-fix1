ALTER TABLE work_orders
    ADD COLUMN IF NOT EXISTS location_id uuid;

ALTER TABLE work_orders
    ADD COLUMN IF NOT EXISTS work_location_note text;

ALTER TABLE completion_acts
    ADD COLUMN IF NOT EXISTS repair_acceptance_id uuid;

CREATE TABLE IF NOT EXISTS repair_acceptances (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    work_order_id uuid NOT NULL,
    stage varchar(32) NOT NULL,
    status varchar(32) NOT NULL DEFAULT 'DRAFT',
    accepted_by_id uuid,
    handed_over_by_id uuid,
    acceptance_started_at timestamptz,
    accepted_at timestamptz,
    run_in_required boolean NOT NULL DEFAULT false,
    run_in_shifts_required integer,
    run_in_started_at timestamptz,
    run_in_completed_at timestamptz,
    quality_grade varchar(32),
    performance_before text,
    performance_after text,
    quality_before text,
    quality_after text,
    remarks text
);

CREATE TABLE IF NOT EXISTS repair_acceptance_defects (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    acceptance_id uuid NOT NULL REFERENCES repair_acceptances(id),
    defect_id uuid,
    description text,
    critical boolean NOT NULL DEFAULT false,
    status varchar(32) NOT NULL DEFAULT 'OPEN',
    resolved_at timestamptz,
    remarks text
);

CREATE INDEX IF NOT EXISTS idx_work_orders_location_id
    ON work_orders(location_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_repair_acceptances_work_order
    ON repair_acceptances(work_order_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_repair_acceptances_final_accepted
    ON repair_acceptances(work_order_id, stage, status)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_repair_acceptance_defects_acceptance
    ON repair_acceptance_defects(acceptance_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_repair_acceptance_defects_open_critical
    ON repair_acceptance_defects(acceptance_id, status)
    WHERE is_deleted = false AND critical = true;
