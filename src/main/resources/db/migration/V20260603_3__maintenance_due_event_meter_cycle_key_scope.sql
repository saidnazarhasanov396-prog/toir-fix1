DROP INDEX IF EXISTS uq_maintenance_due_events_cycle_active;

CREATE UNIQUE INDEX IF NOT EXISTS uq_maintenance_due_events_equipment_regulation_cycle_active
    ON maintenance_due_events (equipment_id, regulation_id, cycle_key)
    WHERE is_deleted = false;

UPDATE maintenance_due_events
SET status = 'DETECTED',
    updated_at = now()
WHERE is_deleted = false
  AND due_status = 'BLOCKED'
  AND status = 'AWAITING_APPROVAL';
