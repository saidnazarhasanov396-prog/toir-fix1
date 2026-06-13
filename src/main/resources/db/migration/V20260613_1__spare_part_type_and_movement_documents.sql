ALTER TABLE spare_parts
    ADD COLUMN IF NOT EXISTS type varchar(50);

UPDATE spare_parts
SET type = 'OTHER'
WHERE type IS NULL;

ALTER TABLE spare_parts
    ALTER COLUMN type SET DEFAULT 'OTHER',
    ALTER COLUMN type SET NOT NULL;

ALTER TABLE stock_movements
    ADD COLUMN IF NOT EXISTS unit varchar(30),
    ADD COLUMN IF NOT EXISTS unit_price numeric(19,2),
    ADD COLUMN IF NOT EXISTS total_amount numeric(19,2),
    ADD COLUMN IF NOT EXISTS responsible_person_id uuid,
    ADD COLUMN IF NOT EXISTS taken_by_id uuid,
    ADD COLUMN IF NOT EXISTS supplier_name varchar(255),
    ADD COLUMN IF NOT EXISTS movement_date date,
    ADD COLUMN IF NOT EXISTS comment text,
    ADD COLUMN IF NOT EXISTS department_id uuid;

UPDATE stock_movements
SET movement_date = CAST(occurred_at AS date)
WHERE movement_date IS NULL
  AND occurred_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_stock_movements_type
    ON stock_movements (type)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_stock_movements_spare_part_id
    ON stock_movements (spare_part_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_stock_movements_warehouse_id
    ON stock_movements (warehouse_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_stock_movements_movement_date
    ON stock_movements (movement_date)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_stock_movements_responsible_person_id
    ON stock_movements (responsible_person_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_stock_movements_work_order_id
    ON stock_movements (work_order_id)
    WHERE is_deleted = false;
