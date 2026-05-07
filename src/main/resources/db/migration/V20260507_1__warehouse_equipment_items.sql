CREATE TABLE IF NOT EXISTS warehouse_equipment_items (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    warehouse_id uuid NOT NULL,
    equipment_id uuid NOT NULL,
    status varchar(64) NOT NULL,
    active boolean NOT NULL DEFAULT true
);

CREATE INDEX IF NOT EXISTS idx_warehouse_equipment_items_warehouse_id
    ON warehouse_equipment_items (warehouse_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_warehouse_equipment_items_equipment_id
    ON warehouse_equipment_items (equipment_id)
    WHERE is_deleted = false;

CREATE UNIQUE INDEX IF NOT EXISTS uq_warehouse_equipment_items_active_equipment
    ON warehouse_equipment_items (equipment_id)
    WHERE active = true AND is_deleted = false;
