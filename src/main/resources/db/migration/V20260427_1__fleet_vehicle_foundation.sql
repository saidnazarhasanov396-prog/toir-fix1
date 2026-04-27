ALTER TABLE equipment
    ADD COLUMN IF NOT EXISTS category varchar(64) NOT NULL DEFAULT 'PRODUCTION_EQUIPMENT';

UPDATE equipment
SET category = 'PRODUCTION_EQUIPMENT'
WHERE category IS NULL;

ALTER TABLE equipment
    ALTER COLUMN category SET DEFAULT 'PRODUCTION_EQUIPMENT';

ALTER TABLE equipment
    ALTER COLUMN category SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_equipment_category
    ON equipment (category)
    WHERE is_deleted = false;
