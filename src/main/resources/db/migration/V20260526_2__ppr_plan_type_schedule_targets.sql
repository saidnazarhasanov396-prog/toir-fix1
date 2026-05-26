ALTER TABLE ppr_plans
    ADD COLUMN IF NOT EXISTS ppr_type varchar(64),
    ADD COLUMN IF NOT EXISTS schedule_type varchar(64),
    ADD COLUMN IF NOT EXISTS frequency varchar(64),
    ADD COLUMN IF NOT EXISTS interval_hours bigint,
    ADD COLUMN IF NOT EXISTS scope_type varchar(64);

CREATE INDEX IF NOT EXISTS idx_ppr_plans_ppr_type
    ON ppr_plans (ppr_type)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_ppr_plans_schedule_type
    ON ppr_plans (schedule_type)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_ppr_plans_scope_type
    ON ppr_plans (scope_type)
    WHERE is_deleted = false;

CREATE TABLE IF NOT EXISTS ppr_plan_targets (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    plan_id uuid NOT NULL,
    target_type varchar(64) NOT NULL,
    equipment_id uuid,
    equipment_type_id uuid,
    CONSTRAINT fk_ppr_plan_targets_plan
        FOREIGN KEY (plan_id) REFERENCES ppr_plans(id),
    CONSTRAINT fk_ppr_plan_targets_equipment
        FOREIGN KEY (equipment_id) REFERENCES equipment(id),
    CONSTRAINT fk_ppr_plan_targets_equipment_type
        FOREIGN KEY (equipment_type_id) REFERENCES equipment_types(id),
    CONSTRAINT chk_ppr_plan_targets_equipment_xor_type
        CHECK (
            (target_type = 'EQUIPMENT' AND equipment_id IS NOT NULL AND equipment_type_id IS NULL)
            OR
            (target_type = 'EQUIPMENT_TYPE' AND equipment_type_id IS NOT NULL AND equipment_id IS NULL)
        )
);

CREATE INDEX IF NOT EXISTS idx_ppr_plan_targets_plan
    ON ppr_plan_targets (plan_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_ppr_plan_targets_equipment
    ON ppr_plan_targets (equipment_id)
    WHERE is_deleted = false AND equipment_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_ppr_plan_targets_equipment_type
    ON ppr_plan_targets (equipment_type_id)
    WHERE is_deleted = false AND equipment_type_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_ppr_plan_targets_active_equipment
    ON ppr_plan_targets (plan_id, equipment_id)
    WHERE is_deleted = false AND target_type = 'EQUIPMENT' AND equipment_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_ppr_plan_targets_active_equipment_type
    ON ppr_plan_targets (plan_id, equipment_type_id)
    WHERE is_deleted = false AND target_type = 'EQUIPMENT_TYPE' AND equipment_type_id IS NOT NULL;
