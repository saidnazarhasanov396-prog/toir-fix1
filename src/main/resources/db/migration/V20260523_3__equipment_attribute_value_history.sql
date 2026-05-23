CREATE TABLE IF NOT EXISTS equipment_attribute_value_history (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    equipment_id uuid NOT NULL,
    attribute_definition_id uuid NOT NULL,
    attribute_key varchar(128) NOT NULL,
    attribute_label varchar(255) NOT NULL,
    old_value text,
    new_value text,
    changed_by uuid,
    changed_at timestamptz NOT NULL,
    source varchar(50) NOT NULL,
    reason text,
    CONSTRAINT fk_equipment_attribute_value_history_equipment
        FOREIGN KEY (equipment_id) REFERENCES equipment(id),
    CONSTRAINT fk_equipment_attribute_value_history_definition
        FOREIGN KEY (attribute_definition_id) REFERENCES equipment_attribute_definitions(id)
);

CREATE INDEX IF NOT EXISTS idx_equipment_attribute_value_history_equipment_id
    ON equipment_attribute_value_history (equipment_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_equipment_attribute_value_history_definition_id
    ON equipment_attribute_value_history (attribute_definition_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_equipment_attribute_value_history_changed_at
    ON equipment_attribute_value_history (changed_at)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_equipment_attribute_value_history_changed_by
    ON equipment_attribute_value_history (changed_by)
    WHERE is_deleted = false AND changed_by IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_equipment_attribute_value_history_equipment_changed_at
    ON equipment_attribute_value_history (equipment_id, changed_at DESC)
    WHERE is_deleted = false;
