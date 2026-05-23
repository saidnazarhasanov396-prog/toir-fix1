CREATE TABLE IF NOT EXISTS equipment_attribute_required_criticality (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    attribute_definition_id uuid NOT NULL,
    criticality_class_id uuid NOT NULL,
    CONSTRAINT fk_equipment_attribute_required_criticality_definition
        FOREIGN KEY (attribute_definition_id) REFERENCES equipment_attribute_definitions(id),
    CONSTRAINT fk_equipment_attribute_required_criticality_class
        FOREIGN KEY (criticality_class_id) REFERENCES criticality_classes(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_equipment_attribute_required_criticality_active
    ON equipment_attribute_required_criticality (attribute_definition_id, criticality_class_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_equipment_attribute_required_criticality_definition
    ON equipment_attribute_required_criticality (attribute_definition_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_equipment_attribute_required_criticality_class
    ON equipment_attribute_required_criticality (criticality_class_id)
    WHERE is_deleted = false;
