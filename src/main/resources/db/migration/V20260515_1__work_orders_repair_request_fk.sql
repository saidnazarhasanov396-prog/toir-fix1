ALTER TABLE work_orders
    ADD COLUMN IF NOT EXISTS repair_request_id uuid;

CREATE INDEX IF NOT EXISTS idx_work_orders_repair_request_id
    ON work_orders (repair_request_id)
    WHERE is_deleted = false;

DO
$$
DECLARE
    orphan_count bigint;
BEGIN
    SELECT COUNT(*)
    INTO orphan_count
    FROM work_orders w
             LEFT JOIN repair_requests r ON r.id = w.repair_request_id
    WHERE w.repair_request_id IS NOT NULL
      AND r.id IS NULL;

    IF orphan_count > 0 THEN
        RAISE EXCEPTION
            'Cannot add FK fk_work_orders_repair_request: found % orphan work_orders.repair_request_id rows',
            orphan_count
            USING HINT = 'Fix or null orphan work_orders.repair_request_id values, then rerun migration.';
    END IF;
END
$$;

DO
$$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        WHERE c.contype = 'f'
          AND c.conrelid = 'work_orders'::regclass
          AND pg_get_constraintdef(c.oid) ILIKE 'foreign key (repair_request_id) references repair_requests%'
    ) THEN
        ALTER TABLE work_orders
            ADD CONSTRAINT fk_work_orders_repair_request
                FOREIGN KEY (repair_request_id) REFERENCES repair_requests (id);
    END IF;
END
$$;
