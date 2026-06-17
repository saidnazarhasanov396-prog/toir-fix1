ALTER TABLE maintenance_operations
    ADD COLUMN IF NOT EXISTS specialist_id uuid;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_maintenance_operations_specialist'
    ) THEN
        ALTER TABLE maintenance_operations
            ADD CONSTRAINT fk_maintenance_operations_specialist
                FOREIGN KEY (specialist_id) REFERENCES users (id);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS maintenance_template_equipment_types (
    template_id uuid NOT NULL REFERENCES maintenance_templates (id) ON DELETE CASCADE,
    equipment_type_id uuid NOT NULL REFERENCES equipment_types (id),
    CONSTRAINT uq_maintenance_template_equipment_types UNIQUE (template_id, equipment_type_id)
);

CREATE INDEX IF NOT EXISTS idx_maintenance_template_equipment_types_template
    ON maintenance_template_equipment_types (template_id);

CREATE INDEX IF NOT EXISTS idx_maintenance_template_equipment_types_equipment_type
    ON maintenance_template_equipment_types (equipment_type_id);

INSERT INTO maintenance_template_equipment_types (template_id, equipment_type_id)
SELECT mt.id, mt.equipment_type_id
FROM maintenance_templates mt
WHERE mt.equipment_type_id IS NOT NULL
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS repair_request_templates (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,
    repair_request_id uuid NOT NULL REFERENCES repair_requests (id) ON DELETE CASCADE,
    template_id uuid NOT NULL REFERENCES maintenance_templates (id),
    sequence integer NOT NULL,
    CONSTRAINT uq_repair_request_templates UNIQUE (repair_request_id, template_id)
);

CREATE INDEX IF NOT EXISTS idx_repair_request_templates_request
    ON repair_request_templates (repair_request_id, sequence)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_repair_request_templates_template
    ON repair_request_templates (template_id)
    WHERE is_deleted = false;

INSERT INTO repair_request_templates (
    id,
    created_at,
    updated_at,
    is_deleted,
    repair_request_id,
    template_id,
    sequence
)
SELECT
    gen_random_uuid(),
    COALESCE(rr.created_at, now()),
    COALESCE(rr.updated_at, now()),
    false,
    rr.id,
    rr.template_id,
    1
FROM repair_requests rr
WHERE rr.template_id IS NOT NULL
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS repair_request_template_actions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,
    repair_request_id uuid NOT NULL REFERENCES repair_requests (id) ON DELETE CASCADE,
    template_id uuid NOT NULL REFERENCES maintenance_templates (id),
    operation_id uuid REFERENCES maintenance_operations (id),
    action_id uuid REFERENCES maintenance_actions (id),
    specialist_id uuid REFERENCES users (id),
    sequence integer NOT NULL,
    custom_name varchar(255),
    name_snapshot varchar(255) NOT NULL,
    duration_hours double precision,
    required_skill varchar(255)
);

CREATE INDEX IF NOT EXISTS idx_repair_request_template_actions_request
    ON repair_request_template_actions (repair_request_id, sequence)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_repair_request_template_actions_template
    ON repair_request_template_actions (template_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_repair_request_template_actions_operation
    ON repair_request_template_actions (operation_id)
    WHERE operation_id IS NOT NULL AND is_deleted = false;
