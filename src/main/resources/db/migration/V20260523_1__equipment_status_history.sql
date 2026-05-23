CREATE TABLE IF NOT EXISTS equipment_status_history (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    equipment_id uuid NOT NULL,
    from_status varchar(50) NOT NULL,
    to_status varchar(50) NOT NULL,
    reason varchar(1000) NOT NULL,
    source varchar(50) NOT NULL,
    changed_by uuid,
    changed_at timestamptz NOT NULL,
    related_entity_type varchar(100),
    related_entity_id uuid,
    note varchar(1000),
    CONSTRAINT fk_equipment_status_history_equipment
        FOREIGN KEY (equipment_id) REFERENCES equipment(id)
);

CREATE INDEX IF NOT EXISTS idx_equipment_status_history_equipment_id
    ON equipment_status_history (equipment_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_equipment_status_history_changed_at
    ON equipment_status_history (changed_at)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_equipment_status_history_source
    ON equipment_status_history (source)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_equipment_status_history_related_entity
    ON equipment_status_history (related_entity_type, related_entity_id)
    WHERE is_deleted = false AND related_entity_type IS NOT NULL AND related_entity_id IS NOT NULL;
