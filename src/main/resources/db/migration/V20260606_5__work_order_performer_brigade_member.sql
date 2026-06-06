ALTER TABLE work_orders
    ADD COLUMN IF NOT EXISTS brigade_member_id uuid;

CREATE INDEX IF NOT EXISTS idx_work_orders_brigade_member_id
    ON work_orders (brigade_member_id)
    WHERE brigade_member_id IS NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_work_orders_brigade_member'
          AND conrelid = 'work_orders'::regclass
    ) THEN
        ALTER TABLE work_orders
            ADD CONSTRAINT fk_work_orders_brigade_member
            FOREIGN KEY (brigade_member_id) REFERENCES brigade_members (id);
    END IF;
END $$;
