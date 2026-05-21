CREATE TABLE IF NOT EXISTS equipment_attribute_definitions (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    equipment_type_id uuid NOT NULL,
    attribute_key varchar(128) NOT NULL,
    label varchar(255) NOT NULL,
    label_ru varchar(255),
    label_uz varchar(255),
    data_type varchar(32) NOT NULL,
    unit varchar(64),
    is_required boolean NOT NULL DEFAULT false,
    min_value double precision,
    max_value double precision,
    options_json text,
    group_name varchar(128),
    sort_order integer NOT NULL DEFAULT 0,
    CONSTRAINT fk_equipment_attribute_definitions_type
        FOREIGN KEY (equipment_type_id) REFERENCES equipment_types(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_equipment_attribute_definitions_type_key_active
    ON equipment_attribute_definitions (equipment_type_id, attribute_key)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_equipment_attribute_definitions_type
    ON equipment_attribute_definitions (equipment_type_id, sort_order)
    WHERE is_deleted = false;

CREATE TABLE IF NOT EXISTS equipment_attribute_values (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    equipment_id uuid NOT NULL,
    attribute_definition_id uuid NOT NULL,
    value_text text,
    value_number double precision,
    value_date date,
    value_boolean boolean,
    value_option varchar(255),
    value_json text,
    CONSTRAINT fk_equipment_attribute_values_equipment
        FOREIGN KEY (equipment_id) REFERENCES equipment(id),
    CONSTRAINT fk_equipment_attribute_values_definition
        FOREIGN KEY (attribute_definition_id) REFERENCES equipment_attribute_definitions(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_equipment_attribute_values_equipment_definition_active
    ON equipment_attribute_values (equipment_id, attribute_definition_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_equipment_attribute_values_equipment
    ON equipment_attribute_values (equipment_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_equipment_attribute_values_definition_number
    ON equipment_attribute_values (attribute_definition_id, value_number)
    WHERE is_deleted = false AND value_number IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_equipment_attribute_values_definition_option
    ON equipment_attribute_values (attribute_definition_id, value_option)
    WHERE is_deleted = false AND value_option IS NOT NULL;
