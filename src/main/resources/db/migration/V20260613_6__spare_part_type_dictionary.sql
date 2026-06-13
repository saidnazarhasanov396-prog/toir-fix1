CREATE TABLE IF NOT EXISTS spare_part_types (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(100) NOT NULL UNIQUE,
    name varchar(255) NOT NULL,
    description text,
    default_unit varchar(50),
    active boolean NOT NULL DEFAULT true,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now()
);

INSERT INTO spare_part_types (code, name, description, default_unit, active)
VALUES
    ('OIL', 'Oil', 'Lubricants and oils', 'LITER', true),
    ('BEARING', 'Bearing', 'Bearings and bearing assemblies', 'PCS', true),
    ('FILTER', 'Filter', 'Filters and filtration parts', 'PCS', true),
    ('BELT', 'Belt', 'Belts and belt drives', 'METER', true),
    ('CABLE', 'Cable', 'Cables and wiring', 'METER', true),
    ('METAL', 'Metal', 'Metal materials and stock', 'KG', true),
    ('CHEMICAL', 'Chemical', 'Chemicals and process fluids', 'LITER', true),
    ('ELECTRICAL_PART', 'Electrical Part', 'Electrical spare parts', 'PCS', true),
    ('MECHANICAL_PART', 'Mechanical Part', 'Mechanical spare parts', 'PCS', true),
    ('CONSUMABLE', 'Consumable', 'Consumable inventory', 'PCS', true),
    ('GREASE', 'Grease', 'Grease and thick lubricants', 'KG', true),
    ('FASTENER', 'Fastener', 'Bolts, nuts, and fasteners', 'PCS', true),
    ('RAW_MATERIAL', 'Raw Material', 'Raw materials', 'KG', true),
    ('OTHER', 'Other', 'Unclassified spare parts', 'PCS', true)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    description = EXCLUDED.description,
    default_unit = EXCLUDED.default_unit,
    active = EXCLUDED.active,
    updated_at = now();

ALTER TABLE spare_parts
    ADD COLUMN IF NOT EXISTS type_id uuid;

UPDATE spare_parts sp
SET type_id = spt.id
FROM spare_part_types spt
WHERE sp.type_id IS NULL
  AND spt.code = CASE
      WHEN sp.type = 'ELECTRICAL' THEN 'ELECTRICAL_PART'
      WHEN sp.type = 'MECHANICAL' THEN 'MECHANICAL_PART'
      WHEN sp.type IS NULL OR trim(sp.type) = '' THEN 'OTHER'
      ELSE sp.type
  END;

UPDATE spare_parts sp
SET type_id = spt.id
FROM spare_part_types spt
WHERE sp.type_id IS NULL
  AND spt.code = 'OTHER';

ALTER TABLE spare_parts
    ALTER COLUMN type_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_spare_parts_type_id'
          AND conrelid = 'spare_parts'::regclass
    ) THEN
        ALTER TABLE spare_parts
            ADD CONSTRAINT fk_spare_parts_type_id
            FOREIGN KEY (type_id) REFERENCES spare_part_types(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_spare_parts_type_id
    ON spare_parts (type_id)
    WHERE is_deleted = false;
