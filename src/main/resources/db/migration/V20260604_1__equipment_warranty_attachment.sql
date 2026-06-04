ALTER TABLE equipment
    ADD COLUMN IF NOT EXISTS has_warranty BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE equipment
    ADD COLUMN IF NOT EXISTS warranty_attachment_id UUID;

CREATE INDEX IF NOT EXISTS idx_equipment_warranty_attachment_id
    ON equipment (warranty_attachment_id)
    WHERE warranty_attachment_id IS NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_equipment_warranty_attachment'
          AND conrelid = 'equipment'::regclass
    ) THEN
        ALTER TABLE equipment
            ADD CONSTRAINT fk_equipment_warranty_attachment
            FOREIGN KEY (warranty_attachment_id) REFERENCES file_assets (id);
    END IF;
END $$;
