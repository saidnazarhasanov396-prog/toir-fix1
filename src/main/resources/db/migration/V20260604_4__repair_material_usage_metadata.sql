ALTER TABLE IF EXISTS repair_material_usages
    ADD COLUMN IF NOT EXISTS stock_movement_id uuid,
    ADD COLUMN IF NOT EXISTS issued_by_id uuid,
    ADD COLUMN IF NOT EXISTS issued_at timestamp(6) with time zone,
    ADD COLUMN IF NOT EXISTS notes text;

CREATE INDEX IF NOT EXISTS idx_repair_material_usages_stock_movement_id
    ON repair_material_usages (stock_movement_id);

CREATE INDEX IF NOT EXISTS idx_repair_material_usages_issued_by_id
    ON repair_material_usages (issued_by_id);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_repair_material_usages_stock_movement'
    ) THEN
        ALTER TABLE repair_material_usages
            ADD CONSTRAINT fk_repair_material_usages_stock_movement
                FOREIGN KEY (stock_movement_id) REFERENCES stock_movements (id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_repair_material_usages_issued_by'
    ) THEN
        ALTER TABLE repair_material_usages
            ADD CONSTRAINT fk_repair_material_usages_issued_by
                FOREIGN KEY (issued_by_id) REFERENCES users (id);
    END IF;
END $$;
