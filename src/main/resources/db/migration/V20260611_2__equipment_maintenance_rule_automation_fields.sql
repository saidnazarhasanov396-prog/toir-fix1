ALTER TABLE equipment_maintenance_rules
    ADD COLUMN IF NOT EXISTS initial_schedule_policy VARCHAR(50),
    ADD COLUMN IF NOT EXISTS automation_action VARCHAR(50),
    ADD COLUMN IF NOT EXISTS approval_result_action VARCHAR(50),
    ADD COLUMN IF NOT EXISTS duplicate_policy VARCHAR(50),
    ADD COLUMN IF NOT EXISTS lead_time_days INTEGER,
    ADD COLUMN IF NOT EXISTS lead_meter_percent DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS default_department_id UUID,
    ADD COLUMN IF NOT EXISTS default_responsible_id UUID,
    ADD COLUMN IF NOT EXISTS default_priority VARCHAR(50),
    ADD COLUMN IF NOT EXISTS requires_approval BOOLEAN,
    ADD COLUMN IF NOT EXISTS approval_role VARCHAR(255),
    ADD COLUMN IF NOT EXISTS approval_permission VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_equipment_maintenance_rules_equipment_active_standalone
    ON equipment_maintenance_rules (equipment_id, is_active)
    WHERE is_deleted = false AND base_regulation_id IS NULL;
