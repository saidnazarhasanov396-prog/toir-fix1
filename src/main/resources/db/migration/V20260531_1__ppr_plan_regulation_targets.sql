ALTER TABLE ppr_plan_targets
    ADD COLUMN IF NOT EXISTS regulation_id uuid;

ALTER TABLE ppr_plan_targets
    DROP CONSTRAINT IF EXISTS fk_ppr_plan_targets_regulation;

ALTER TABLE ppr_plan_targets
    ADD CONSTRAINT fk_ppr_plan_targets_regulation
        FOREIGN KEY (regulation_id) REFERENCES maintenance_regulations(id);

ALTER TABLE ppr_plan_targets
    DROP CONSTRAINT IF EXISTS chk_ppr_plan_targets_equipment_xor_type;

ALTER TABLE ppr_plan_targets
    ADD CONSTRAINT chk_ppr_plan_targets_single_target
        CHECK (
            (target_type = 'EQUIPMENT' AND equipment_id IS NOT NULL AND equipment_type_id IS NULL AND regulation_id IS NULL)
            OR
            (target_type = 'EQUIPMENT_TYPE' AND equipment_type_id IS NOT NULL AND equipment_id IS NULL AND regulation_id IS NULL)
            OR
            (target_type = 'REGULATION' AND regulation_id IS NOT NULL AND equipment_id IS NULL AND equipment_type_id IS NULL)
        );

CREATE INDEX IF NOT EXISTS idx_ppr_plan_targets_regulation
    ON ppr_plan_targets (regulation_id)
    WHERE is_deleted = false AND regulation_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_ppr_plan_targets_active_regulation
    ON ppr_plan_targets (plan_id, regulation_id)
    WHERE is_deleted = false AND target_type = 'REGULATION' AND regulation_id IS NOT NULL;
