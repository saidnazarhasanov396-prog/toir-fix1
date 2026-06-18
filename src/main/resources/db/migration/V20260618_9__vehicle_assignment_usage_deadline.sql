ALTER TABLE vehicle_details
    ADD COLUMN IF NOT EXISTS assigned_driver_usage_limit_minutes integer,
    ADD COLUMN IF NOT EXISTS assigned_driver_assigned_by uuid,
    ADD COLUMN IF NOT EXISTS assigned_driver_assigned_at timestamptz;

ALTER TABLE vehicle_details
    DROP CONSTRAINT IF EXISTS chk_vehicle_details_assigned_driver_usage_limit_minutes;

ALTER TABLE vehicle_details
    ADD CONSTRAINT chk_vehicle_details_assigned_driver_usage_limit_minutes
    CHECK (
        assigned_driver_usage_limit_minutes IS NULL
        OR assigned_driver_usage_limit_minutes > 0
    );

ALTER TABLE equipment_usage_sessions
    ADD COLUMN IF NOT EXISTS usage_limit_minutes integer,
    ADD COLUMN IF NOT EXISTS due_at timestamptz,
    ADD COLUMN IF NOT EXISTS assignment_actor_user_id uuid,
    ADD COLUMN IF NOT EXISTS overdue_notified_at timestamptz;

ALTER TABLE equipment_usage_sessions
    DROP CONSTRAINT IF EXISTS chk_equipment_usage_sessions_usage_limit_minutes;

ALTER TABLE equipment_usage_sessions
    ADD CONSTRAINT chk_equipment_usage_sessions_usage_limit_minutes
    CHECK (
        usage_limit_minutes IS NULL
        OR usage_limit_minutes > 0
    );

CREATE INDEX IF NOT EXISTS idx_equipment_usage_sessions_open_due
    ON equipment_usage_sessions(due_at)
    WHERE is_deleted = false
      AND status = 'OPEN'
      AND due_at IS NOT NULL
      AND overdue_notified_at IS NULL;
