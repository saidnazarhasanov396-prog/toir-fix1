ALTER TABLE equipment
    ADD COLUMN IF NOT EXISTS warranty_start_date date NULL;

ALTER TABLE equipment
    ADD COLUMN IF NOT EXISTS warranty_end_date date NULL;
