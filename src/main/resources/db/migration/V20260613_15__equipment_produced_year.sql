ALTER TABLE equipment
    ADD COLUMN IF NOT EXISTS produced_year integer;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_equipment_produced_year_range'
    ) THEN
        ALTER TABLE equipment
            ADD CONSTRAINT chk_equipment_produced_year_range
                CHECK (produced_year IS NULL OR (produced_year >= 1900 AND produced_year <= 2100));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_equipment_produced_year
    ON equipment (produced_year)
    WHERE is_deleted = false;
