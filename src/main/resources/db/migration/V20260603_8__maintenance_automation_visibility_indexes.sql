CREATE INDEX IF NOT EXISTS idx_notifications_maintenance_due_event_dedup
    ON notifications (entity_type, entity_id, title, recipient_id, status)
    WHERE is_deleted = false
      AND entity_type = 'MaintenanceDueEvent';
