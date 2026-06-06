DO
$$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_warehouse_stocks_quantity_nonnegative'
          AND conrelid = 'warehouse_stocks'::regclass
    ) THEN
        ALTER TABLE warehouse_stocks
            ADD CONSTRAINT chk_warehouse_stocks_quantity_nonnegative
                CHECK (quantity >= 0) NOT VALID;
    END IF;
END
$$;

DO
$$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_warehouse_stocks_reserved_qty_nonnegative'
          AND conrelid = 'warehouse_stocks'::regclass
    ) THEN
        ALTER TABLE warehouse_stocks
            ADD CONSTRAINT chk_warehouse_stocks_reserved_qty_nonnegative
                CHECK (reserved_qty >= 0) NOT VALID;
    END IF;
END
$$;

DO
$$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_warehouse_stocks_reserved_not_over_quantity'
          AND conrelid = 'warehouse_stocks'::regclass
    ) THEN
        ALTER TABLE warehouse_stocks
            ADD CONSTRAINT chk_warehouse_stocks_reserved_not_over_quantity
                CHECK (reserved_qty <= quantity) NOT VALID;
    END IF;
END
$$;
