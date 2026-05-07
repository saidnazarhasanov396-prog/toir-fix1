ALTER TABLE work_orders
    ADD COLUMN IF NOT EXISTS warehouse_id uuid;

ALTER TABLE work_orders
    ADD COLUMN IF NOT EXISTS replacement_equipment_id uuid;

CREATE INDEX IF NOT EXISTS idx_work_orders_warehouse_id
    ON work_orders (warehouse_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_work_orders_replacement_equipment_id
    ON work_orders (replacement_equipment_id)
    WHERE is_deleted = false;
