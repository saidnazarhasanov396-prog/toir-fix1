CREATE TABLE IF NOT EXISTS equipment_manual_attributes (
    id uuid PRIMARY KEY,
    equipment_id uuid NOT NULL REFERENCES equipment(id),
    attribute_key varchar(100) NOT NULL,
    attribute_value text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false
);

CREATE INDEX IF NOT EXISTS idx_equipment_manual_attributes_equipment_id
    ON equipment_manual_attributes(equipment_id);

CREATE INDEX IF NOT EXISTS idx_equipment_manual_attributes_lower_key
    ON equipment_manual_attributes(lower(attribute_key));

CREATE INDEX IF NOT EXISTS idx_equipment_manual_attributes_is_deleted
    ON equipment_manual_attributes(is_deleted);

CREATE UNIQUE INDEX IF NOT EXISTS ux_equipment_manual_attributes_equipment_key_active
    ON equipment_manual_attributes(equipment_id, lower(attribute_key))
    WHERE is_deleted = false;
