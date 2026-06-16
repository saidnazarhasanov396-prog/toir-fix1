ALTER TABLE equipment
    ADD COLUMN IF NOT EXISTS lifetime_counter_type varchar(64),
    ADD COLUMN IF NOT EXISTS lifetime_meter_id uuid,
    ADD COLUMN IF NOT EXISTS lifetime_limit_value double precision,
    ADD COLUMN IF NOT EXISTS lifetime_baseline_value double precision,
    ADD COLUMN IF NOT EXISTS lifetime_warning_percent double precision;

UPDATE equipment
SET lifetime_counter_type = COALESCE(lifetime_counter_type, 'ENGINE_HOURS'),
    lifetime_limit_value = COALESCE(lifetime_limit_value, expected_lifetime_hours::double precision),
    lifetime_baseline_value = COALESCE(lifetime_baseline_value, 0),
    lifetime_warning_percent = COALESCE(lifetime_warning_percent, 10)
WHERE expected_lifetime_hours IS NOT NULL
  AND expected_lifetime_hours > 0;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_equipment_lifetime_limit_positive'
    ) THEN
        ALTER TABLE equipment
            ADD CONSTRAINT chk_equipment_lifetime_limit_positive
                CHECK (lifetime_limit_value IS NULL OR lifetime_limit_value > 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_equipment_lifetime_baseline_non_negative'
    ) THEN
        ALTER TABLE equipment
            ADD CONSTRAINT chk_equipment_lifetime_baseline_non_negative
                CHECK (lifetime_baseline_value IS NULL OR lifetime_baseline_value >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_equipment_lifetime_warning_positive'
    ) THEN
        ALTER TABLE equipment
            ADD CONSTRAINT chk_equipment_lifetime_warning_positive
                CHECK (lifetime_warning_percent IS NULL OR lifetime_warning_percent > 0);
    END IF;
END $$;
