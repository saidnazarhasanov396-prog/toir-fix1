ALTER TABLE equipment
    ADD COLUMN IF NOT EXISTS days_of_resource_remaining BIGINT;
