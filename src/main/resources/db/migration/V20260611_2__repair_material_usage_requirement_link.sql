ALTER TABLE repair_material_usages
    ADD COLUMN IF NOT EXISTS requirement_id uuid,
    ADD COLUMN IF NOT EXISTS replaced_spare_part_id uuid;

CREATE INDEX IF NOT EXISTS ix_repair_material_usages_requirement_id
    ON repair_material_usages (requirement_id)
    WHERE requirement_id IS NOT NULL AND is_deleted = false;
