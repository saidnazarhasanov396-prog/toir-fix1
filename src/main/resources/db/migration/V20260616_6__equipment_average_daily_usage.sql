ALTER TABLE equipment
    ADD COLUMN IF NOT EXISTS average_daily_usage double precision;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_equipment_average_daily_usage_positive'
    ) THEN
        ALTER TABLE equipment
            ADD CONSTRAINT chk_equipment_average_daily_usage_positive
                CHECK (average_daily_usage IS NULL OR average_daily_usage > 0);
    END IF;
END $$;
