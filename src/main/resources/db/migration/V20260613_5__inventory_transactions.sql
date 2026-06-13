CREATE TABLE IF NOT EXISTS inventory_transactions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    type varchar(30) NOT NULL,
    warehouse_id uuid NOT NULL,
    spare_part_id uuid NOT NULL,
    quantity numeric(19,4) NOT NULL,
    unit varchar(30),
    unit_price numeric(19,2),
    total_amount numeric(19,2),
    supplier_name varchar(255),
    taken_by_id uuid,
    responsible_person_id uuid,
    department_id uuid,
    work_order_id uuid,
    transaction_date date NOT NULL,
    document_number varchar(100),
    comment text,
    created_at timestamp NOT NULL DEFAULT now(),
    created_by uuid
);

CREATE INDEX IF NOT EXISTS idx_inventory_tx_type
    ON inventory_transactions (type);

CREATE INDEX IF NOT EXISTS idx_inventory_tx_date
    ON inventory_transactions (transaction_date DESC, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_inventory_tx_spare_part
    ON inventory_transactions (spare_part_id);

CREATE INDEX IF NOT EXISTS idx_inventory_tx_warehouse
    ON inventory_transactions (warehouse_id);

CREATE INDEX IF NOT EXISTS idx_inventory_tx_work_order
    ON inventory_transactions (work_order_id);

CREATE INDEX IF NOT EXISTS idx_inventory_tx_department
    ON inventory_transactions (department_id);

CREATE INDEX IF NOT EXISTS idx_inventory_tx_responsible
    ON inventory_transactions (responsible_person_id);

CREATE INDEX IF NOT EXISTS idx_inventory_tx_taken_by
    ON inventory_transactions (taken_by_id);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_inventory_tx_warehouse'
          AND conrelid = 'inventory_transactions'::regclass
    ) THEN
        ALTER TABLE inventory_transactions
            ADD CONSTRAINT fk_inventory_tx_warehouse
            FOREIGN KEY (warehouse_id) REFERENCES warehouses(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_inventory_tx_spare_part'
          AND conrelid = 'inventory_transactions'::regclass
    ) THEN
        ALTER TABLE inventory_transactions
            ADD CONSTRAINT fk_inventory_tx_spare_part
            FOREIGN KEY (spare_part_id) REFERENCES spare_parts(id);
    END IF;
END $$;
