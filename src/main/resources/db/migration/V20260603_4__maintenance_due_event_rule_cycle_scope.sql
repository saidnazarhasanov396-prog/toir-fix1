DROP INDEX IF EXISTS uq_maintenance_due_events_equipment_regulation_cycle_active;

CREATE UNIQUE INDEX IF NOT EXISTS uq_maintenance_due_events_equipment_regulation_cycle_active
    ON maintenance_due_events (equipment_id, regulation_id, cycle_key)
    WHERE is_deleted = false
      AND equipment_maintenance_rule_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_maintenance_due_events_equipment_rule_cycle_active
    ON maintenance_due_events (equipment_id, equipment_maintenance_rule_id, cycle_key)
    WHERE is_deleted = false
      AND equipment_maintenance_rule_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_maintenance_due_events_equipment_rule
    ON maintenance_due_events (equipment_id, equipment_maintenance_rule_id)
    WHERE is_deleted = false;
