CREATE TABLE IF NOT EXISTS maintenance_regulation_attribute_conditions (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    regulation_id uuid NOT NULL,
    attribute_key varchar(128) NOT NULL,
    operator varchar(32) NOT NULL,
    value_text text,
    value_number double precision,
    value_date date,
    value_boolean boolean,
    value_option varchar(255),
    CONSTRAINT fk_maintenance_regulation_attribute_conditions_regulation
        FOREIGN KEY (regulation_id) REFERENCES maintenance_regulations(id)
);

CREATE INDEX IF NOT EXISTS idx_maintenance_regulation_attribute_conditions_regulation
    ON maintenance_regulation_attribute_conditions (regulation_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_maintenance_regulation_attribute_conditions_key
    ON maintenance_regulation_attribute_conditions (attribute_key)
    WHERE is_deleted = false;
