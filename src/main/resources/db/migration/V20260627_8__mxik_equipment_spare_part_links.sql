ALTER TABLE equipment
    ADD COLUMN IF NOT EXISTS mxik_id uuid;

ALTER TABLE spare_parts
    ADD COLUMN IF NOT EXISTS mxik_id uuid;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_equipment_mxik'
          AND conrelid = 'equipment'::regclass
    ) THEN
        ALTER TABLE equipment
            ADD CONSTRAINT fk_equipment_mxik
            FOREIGN KEY (mxik_id) REFERENCES mxik(id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_spare_parts_mxik'
          AND conrelid = 'spare_parts'::regclass
    ) THEN
        ALTER TABLE spare_parts
            ADD CONSTRAINT fk_spare_parts_mxik
            FOREIGN KEY (mxik_id) REFERENCES mxik(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_equipment_mxik_id
    ON equipment (mxik_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_spare_parts_mxik_id
    ON spare_parts (mxik_id)
    WHERE is_deleted = false;
