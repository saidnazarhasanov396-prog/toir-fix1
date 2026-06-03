ALTER TABLE maintenance_completion_anchors
    ADD COLUMN IF NOT EXISTS maintenance_due_event_id uuid;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_maintenance_completion_anchors_due_event'
          AND conrelid = 'maintenance_completion_anchors'::regclass
    ) THEN
        ALTER TABLE maintenance_completion_anchors
            ADD CONSTRAINT fk_maintenance_completion_anchors_due_event
                FOREIGN KEY (maintenance_due_event_id)
                    REFERENCES maintenance_due_events(id);
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_maintenance_completion_anchors_due_event
    ON maintenance_completion_anchors (maintenance_due_event_id)
    WHERE is_deleted = false
      AND maintenance_due_event_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_maintenance_completion_anchors_work_order
    ON maintenance_completion_anchors (work_order_id)
    WHERE is_deleted = false
      AND work_order_id IS NOT NULL;
