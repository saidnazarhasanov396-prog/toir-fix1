ALTER TABLE equipment
ADD COLUMN IF NOT EXISTS expected_lifetime_hours BIGINT;
