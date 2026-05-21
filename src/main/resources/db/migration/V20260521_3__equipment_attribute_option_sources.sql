ALTER TABLE equipment_attribute_definitions
    ADD COLUMN IF NOT EXISTS option_source_id uuid;

CREATE TABLE IF NOT EXISTS equipment_attribute_option_sources (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    code varchar(128) NOT NULL,
    name varchar(255) NOT NULL,
    name_ru varchar(255),
    name_uz varchar(255),
    description text
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_equipment_attribute_option_sources_code_active
    ON equipment_attribute_option_sources (code)
    WHERE is_deleted = false;

CREATE TABLE IF NOT EXISTS equipment_attribute_option_items (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    option_source_id uuid NOT NULL,
    option_id varchar(128) NOT NULL,
    label varchar(255) NOT NULL,
    label_ru varchar(255),
    label_uz varchar(255),
    sort_order integer NOT NULL DEFAULT 0,
    active boolean NOT NULL DEFAULT true,
    CONSTRAINT fk_equipment_attribute_option_items_source
        FOREIGN KEY (option_source_id) REFERENCES equipment_attribute_option_sources(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_equipment_attribute_option_items_source_option_active
    ON equipment_attribute_option_items (option_source_id, option_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_equipment_attribute_definitions_option_source
    ON equipment_attribute_definitions (option_source_id)
    WHERE is_deleted = false AND option_source_id IS NOT NULL;
