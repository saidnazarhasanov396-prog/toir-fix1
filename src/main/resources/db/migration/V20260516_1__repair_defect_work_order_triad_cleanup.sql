DO
$$
BEGIN
    -- If legacy defects.request_id exists without defects.repair_request_id,
    -- rename it to the target column name.
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'defects'
          AND column_name = 'request_id'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'defects'
          AND column_name = 'repair_request_id'
    ) THEN
        ALTER TABLE defects RENAME COLUMN request_id TO repair_request_id;
    END IF;
END
$$;

ALTER TABLE defects
    ADD COLUMN IF NOT EXISTS repair_request_id uuid;

DO
$$
BEGIN
    -- If both legacy and target columns exist, copy data then drop legacy.
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'defects'
          AND column_name = 'request_id'
    ) AND EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'defects'
          AND column_name = 'repair_request_id'
    ) THEN
        UPDATE defects
        SET repair_request_id = request_id
        WHERE repair_request_id IS NULL
          AND request_id IS NOT NULL;

        ALTER TABLE defects DROP COLUMN request_id;
    END IF;
END
$$;

ALTER TABLE work_orders
    ADD COLUMN IF NOT EXISTS defect_id uuid;

DO
$$
BEGIN
    -- Migrate inverse defect->work_order link into target work_order->defect link.
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'defects'
          AND column_name = 'work_order_id'
    ) THEN
        EXECUTE
            'UPDATE work_orders wo
             SET defect_id = d.id
             FROM defects d
             WHERE d.work_order_id = wo.id
               AND d.work_order_id IS NOT NULL
               AND wo.defect_id IS NULL';
    END IF;
END
$$;

ALTER TABLE defects
    DROP COLUMN IF EXISTS work_order_id;

ALTER TABLE work_orders
    ADD COLUMN IF NOT EXISTS repair_request_id uuid;

CREATE INDEX IF NOT EXISTS idx_defects_repair_request_id
    ON defects (repair_request_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_work_orders_repair_request_id
    ON work_orders (repair_request_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_work_orders_defect_id
    ON work_orders (defect_id)
    WHERE is_deleted = false;

DO
$$
DECLARE
    orphan_defects_repair_request bigint;
    orphan_work_orders_repair_request bigint;
    orphan_work_orders_defect bigint;
BEGIN
    SELECT COUNT(*)
    INTO orphan_defects_repair_request
    FROM defects d
             LEFT JOIN repair_requests rr ON rr.id = d.repair_request_id
    WHERE d.repair_request_id IS NOT NULL
      AND rr.id IS NULL;

    IF orphan_defects_repair_request > 0 THEN
        RAISE EXCEPTION
            'Cannot add FK fk_defects_repair_request: found % orphan defects.repair_request_id rows',
            orphan_defects_repair_request
            USING HINT = 'Fix or null orphan defects.repair_request_id values, then rerun migration.';
    END IF;

    SELECT COUNT(*)
    INTO orphan_work_orders_repair_request
    FROM work_orders w
             LEFT JOIN repair_requests rr ON rr.id = w.repair_request_id
    WHERE w.repair_request_id IS NOT NULL
      AND rr.id IS NULL;

    IF orphan_work_orders_repair_request > 0 THEN
        RAISE EXCEPTION
            'Cannot add FK fk_work_orders_repair_request: found % orphan work_orders.repair_request_id rows',
            orphan_work_orders_repair_request
            USING HINT = 'Fix or null orphan work_orders.repair_request_id values, then rerun migration.';
    END IF;

    SELECT COUNT(*)
    INTO orphan_work_orders_defect
    FROM work_orders w
             LEFT JOIN defects d ON d.id = w.defect_id
    WHERE w.defect_id IS NOT NULL
      AND d.id IS NULL;

    IF orphan_work_orders_defect > 0 THEN
        RAISE EXCEPTION
            'Cannot add FK fk_work_orders_defect: found % orphan work_orders.defect_id rows',
            orphan_work_orders_defect
            USING HINT = 'Fix or null orphan work_orders.defect_id values, then rerun migration.';
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
          AND c.conrelid = 'defects'::regclass
          AND pg_get_constraintdef(c.oid) ILIKE 'foreign key (repair_request_id) references repair_requests%'
    ) THEN
        ALTER TABLE defects
            ADD CONSTRAINT fk_defects_repair_request
                FOREIGN KEY (repair_request_id) REFERENCES repair_requests (id);
    END IF;

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

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        WHERE c.contype = 'f'
          AND c.conrelid = 'work_orders'::regclass
          AND pg_get_constraintdef(c.oid) ILIKE 'foreign key (defect_id) references defects%'
    ) THEN
        ALTER TABLE work_orders
            ADD CONSTRAINT fk_work_orders_defect
                FOREIGN KEY (defect_id) REFERENCES defects (id);
    END IF;
END
$$;
