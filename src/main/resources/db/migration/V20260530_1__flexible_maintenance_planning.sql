ALTER TABLE maintenance_regulations
    ADD COLUMN IF NOT EXISTS trigger_policy varchar(32) NOT NULL DEFAULT 'ANY',
    ADD COLUMN IF NOT EXISTS recalculation_policy varchar(64) NOT NULL DEFAULT 'FROM_ACTUAL_COMPLETION';

ALTER TABLE equipment_maintenance_rules
    ADD COLUMN IF NOT EXISTS trigger_policy varchar(32) NOT NULL DEFAULT 'ANY',
    ADD COLUMN IF NOT EXISTS recalculation_policy varchar(64) NOT NULL DEFAULT 'FROM_ACTUAL_COMPLETION',
    ADD COLUMN IF NOT EXISTS disables_base_regulation boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS override_reason text;

CREATE TABLE IF NOT EXISTS maintenance_completion_anchors (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    equipment_id uuid NOT NULL,
    regulation_id uuid,
    equipment_maintenance_rule_id uuid,
    work_order_id uuid,
    ppr_task_id uuid,
    performed_at timestamptz NOT NULL,
    planned_due_at timestamptz,
    planned_meter_value numeric(19, 4),
    recalculation_policy varchar(64) NOT NULL DEFAULT 'FROM_ACTUAL_COMPLETION',
    meter_snapshots jsonb NOT NULL DEFAULT '[]'::jsonb,
    source varchar(255) NOT NULL,
    note text,
    CONSTRAINT chk_maintenance_completion_anchors_source_present
        CHECK (regulation_id IS NOT NULL OR equipment_maintenance_rule_id IS NOT NULL)
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_maintenance_completion_anchors_source_present'
          AND conrelid = 'maintenance_completion_anchors'::regclass
    ) THEN
        ALTER TABLE maintenance_completion_anchors
            ADD CONSTRAINT chk_maintenance_completion_anchors_source_present
                CHECK (regulation_id IS NOT NULL OR equipment_maintenance_rule_id IS NOT NULL);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_maintenance_completion_anchors_equipment'
          AND conrelid = 'maintenance_completion_anchors'::regclass
    ) THEN
        ALTER TABLE maintenance_completion_anchors
            ADD CONSTRAINT fk_maintenance_completion_anchors_equipment
                FOREIGN KEY (equipment_id)
                    REFERENCES equipment(id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_maintenance_completion_anchors_regulation'
          AND conrelid = 'maintenance_completion_anchors'::regclass
    ) THEN
        ALTER TABLE maintenance_completion_anchors
            ADD CONSTRAINT fk_maintenance_completion_anchors_regulation
                FOREIGN KEY (regulation_id)
                    REFERENCES maintenance_regulations(id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_maintenance_completion_anchors_rule'
          AND conrelid = 'maintenance_completion_anchors'::regclass
    ) THEN
        ALTER TABLE maintenance_completion_anchors
            ADD CONSTRAINT fk_maintenance_completion_anchors_rule
                FOREIGN KEY (equipment_maintenance_rule_id)
                    REFERENCES equipment_maintenance_rules(id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_maintenance_completion_anchors_work_order'
          AND conrelid = 'maintenance_completion_anchors'::regclass
    ) THEN
        ALTER TABLE maintenance_completion_anchors
            ADD CONSTRAINT fk_maintenance_completion_anchors_work_order
                FOREIGN KEY (work_order_id)
                    REFERENCES work_orders(id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_maintenance_completion_anchors_ppr_task'
          AND conrelid = 'maintenance_completion_anchors'::regclass
    ) THEN
        ALTER TABLE maintenance_completion_anchors
            ADD CONSTRAINT fk_maintenance_completion_anchors_ppr_task
                FOREIGN KEY (ppr_task_id)
                    REFERENCES ppr_tasks(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_maintenance_completion_anchors_equipment_regulation
    ON maintenance_completion_anchors (equipment_id, regulation_id, performed_at DESC)
    WHERE is_deleted = false
      AND regulation_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_maintenance_completion_anchors_equipment_rule
    ON maintenance_completion_anchors (equipment_id, equipment_maintenance_rule_id, performed_at DESC)
    WHERE is_deleted = false
      AND equipment_maintenance_rule_id IS NOT NULL;
