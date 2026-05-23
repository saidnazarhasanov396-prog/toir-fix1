ALTER TABLE work_orders
    ADD COLUMN IF NOT EXISTS equipment_node_id uuid;

CREATE INDEX IF NOT EXISTS idx_work_orders_equipment_node_id
    ON work_orders (equipment_node_id)
    WHERE equipment_node_id IS NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_work_orders_equipment_node'
          AND conrelid = 'work_orders'::regclass
    ) THEN
        ALTER TABLE work_orders
            ADD CONSTRAINT fk_work_orders_equipment_node
            FOREIGN KEY (equipment_node_id) REFERENCES equipment_nodes (id);
    END IF;
END $$;
