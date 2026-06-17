ALTER TABLE equipment_passports
    ADD COLUMN IF NOT EXISTS productivity text;

ALTER TABLE equipment_passports
    DROP COLUMN IF EXISTS throughput;


