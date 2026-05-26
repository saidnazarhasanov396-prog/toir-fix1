CREATE TABLE IF NOT EXISTS maintenance_actions (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    code varchar(128) NOT NULL,
    name varchar(255) NOT NULL,
    category varchar(128),
    default_duration_hours double precision,
    required_skill varchar(255),
    safety_notes text,
    tools_required text,
    spare_parts_required text,
    consumables_required text,
    is_active boolean NOT NULL DEFAULT true
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_maintenance_actions_code_active
    ON maintenance_actions (upper(code))
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_maintenance_actions_category
    ON maintenance_actions (category)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_maintenance_actions_active
    ON maintenance_actions (is_active)
    WHERE is_deleted = false;

ALTER TABLE maintenance_operations
    ADD COLUMN IF NOT EXISTS action_id uuid;

CREATE INDEX IF NOT EXISTS idx_maintenance_operations_action
    ON maintenance_operations (action_id)
    WHERE is_deleted = false;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_maintenance_operations_action'
          AND conrelid = 'maintenance_operations'::regclass
    ) THEN
        ALTER TABLE maintenance_operations
            ADD CONSTRAINT fk_maintenance_operations_action
                FOREIGN KEY (action_id)
                    REFERENCES maintenance_actions(id);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS equipment_maintenance_rules (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    equipment_id uuid NOT NULL,
    base_regulation_id uuid,
    template_id uuid,
    code varchar(128) NOT NULL,
    name varchar(255) NOT NULL,
    description text,
    maintenance_kind varchar(64) NOT NULL,
    normative_labor_hours double precision NOT NULL DEFAULT 0,
    is_active boolean NOT NULL DEFAULT true,
    periodicity_unit varchar(32) NOT NULL,
    periodicity_value integer NOT NULL,
    tolerance_days integer,
    requires_shutdown boolean NOT NULL DEFAULT false,
    trigger_meter_type varchar(64),
    trigger_meter_interval double precision
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_equipment_maintenance_rules_equipment'
          AND conrelid = 'equipment_maintenance_rules'::regclass
    ) THEN
        ALTER TABLE equipment_maintenance_rules
            ADD CONSTRAINT fk_equipment_maintenance_rules_equipment
                FOREIGN KEY (equipment_id)
                    REFERENCES equipment(id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_equipment_maintenance_rules_base_regulation'
          AND conrelid = 'equipment_maintenance_rules'::regclass
    ) THEN
        ALTER TABLE equipment_maintenance_rules
            ADD CONSTRAINT fk_equipment_maintenance_rules_base_regulation
                FOREIGN KEY (base_regulation_id)
                    REFERENCES maintenance_regulations(id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_equipment_maintenance_rules_template'
          AND conrelid = 'equipment_maintenance_rules'::regclass
    ) THEN
        ALTER TABLE equipment_maintenance_rules
            ADD CONSTRAINT fk_equipment_maintenance_rules_template
                FOREIGN KEY (template_id)
                    REFERENCES maintenance_templates(id);
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_equipment_maintenance_rules_code_active
    ON equipment_maintenance_rules (upper(code))
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_equipment_maintenance_rules_equipment
    ON equipment_maintenance_rules (equipment_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_equipment_maintenance_rules_base_regulation
    ON equipment_maintenance_rules (base_regulation_id)
    WHERE is_deleted = false
      AND base_regulation_id IS NOT NULL;

ALTER TABLE ppr_tasks
    ADD COLUMN IF NOT EXISTS equipment_maintenance_rule_id uuid;

ALTER TABLE ppr_tasks
    ALTER COLUMN regulation_id DROP NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_ppr_tasks_has_maintenance_source'
          AND conrelid = 'ppr_tasks'::regclass
    ) THEN
        ALTER TABLE ppr_tasks
            ADD CONSTRAINT chk_ppr_tasks_has_maintenance_source
                CHECK (regulation_id IS NOT NULL OR equipment_maintenance_rule_id IS NOT NULL);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_ppr_tasks_equipment_maintenance_rule
    ON ppr_tasks (equipment_maintenance_rule_id)
    WHERE is_deleted = false
      AND equipment_maintenance_rule_id IS NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_ppr_tasks_equipment_maintenance_rule'
          AND conrelid = 'ppr_tasks'::regclass
    ) THEN
        ALTER TABLE ppr_tasks
            ADD CONSTRAINT fk_ppr_tasks_equipment_maintenance_rule
                FOREIGN KEY (equipment_maintenance_rule_id)
                    REFERENCES equipment_maintenance_rules(id);
    END IF;
END $$;
