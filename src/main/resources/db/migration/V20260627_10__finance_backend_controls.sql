ALTER TABLE maintenance_budgets
    DROP CONSTRAINT IF EXISTS maintenance_budgets_status_check;

ALTER TABLE maintenance_budgets
    ADD CONSTRAINT maintenance_budgets_status_check
    CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'LOCKED', 'CLOSED', 'REJECTED'));

ALTER TABLE actual_costs
    ADD COLUMN IF NOT EXISTS correction_reason text,
    ADD COLUMN IF NOT EXISTS allocation_comment text,
    ADD COLUMN IF NOT EXISTS allocated_by_id uuid,
    ADD COLUMN IF NOT EXISTS allocated_at timestamptz;

CREATE TABLE IF NOT EXISTS budget_events (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    budget_id uuid NOT NULL REFERENCES maintenance_budgets(id),
    budget_line_id uuid REFERENCES budget_lines(id),
    event_type varchar(64) NOT NULL,
    old_values jsonb,
    new_values jsonb,
    actor_user_id uuid,
    comment text,
    occurred_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_budget_events_budget_id
    ON budget_events (budget_id, occurred_at DESC)
    WHERE is_deleted = false;

CREATE TABLE IF NOT EXISTS actual_cost_allocation_events (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    actual_cost_id uuid NOT NULL REFERENCES actual_costs(id),
    old_budget_line_id uuid REFERENCES budget_lines(id),
    new_budget_line_id uuid REFERENCES budget_lines(id),
    actor_user_id uuid,
    comment text NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_actual_cost_allocation_events_cost_id
    ON actual_cost_allocation_events (actual_cost_id, occurred_at DESC)
    WHERE is_deleted = false;
