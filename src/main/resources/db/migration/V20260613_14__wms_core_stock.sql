CREATE TABLE IF NOT EXISTS warehouse_bins (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,

    warehouse_id uuid NOT NULL,
    code varchar(100) NOT NULL,
    zone varchar(64),
    aisle varchar(64),
    rack varchar(64),
    shelf_level varchar(64),
    bin_type varchar(64),
    max_weight_kg numeric(19,4),
    max_volume_m3 numeric(19,4),
    coord_x numeric(19,4),
    coord_y numeric(19,4),
    width numeric(19,4),
    height numeric(19,4),
    blocked boolean NOT NULL DEFAULT false,
    block_reason varchar(255),
    blocked_at timestamptz,
    frozen boolean NOT NULL DEFAULT false,
    active boolean NOT NULL DEFAULT true,
    travel_sequence integer,
    bin_level integer
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_warehouse_bins_warehouse'
    ) THEN
        ALTER TABLE warehouse_bins
            ADD CONSTRAINT fk_warehouse_bins_warehouse
                FOREIGN KEY (warehouse_id) REFERENCES warehouses (id);
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_warehouse_bins_warehouse_code
    ON warehouse_bins (warehouse_id, lower(code))
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_warehouse_bins_warehouse
    ON warehouse_bins (warehouse_id)
    WHERE is_deleted = false;

CREATE TABLE IF NOT EXISTS warehouse_stock_balances (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,

    warehouse_id uuid NOT NULL,
    spare_part_id uuid NOT NULL,
    bin_id uuid,
    lot_number varchar(100),
    serial_number varchar(128),
    identity_key varchar(512) NOT NULL,
    expiry_date date,
    qty_on_hand numeric(19,4) NOT NULL DEFAULT 0,
    qty_reserved numeric(19,4) NOT NULL DEFAULT 0,
    avg_cost numeric(19,4),
    version bigint
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_warehouse_stock_balances_warehouse'
    ) THEN
        ALTER TABLE warehouse_stock_balances
            ADD CONSTRAINT fk_warehouse_stock_balances_warehouse
                FOREIGN KEY (warehouse_id) REFERENCES warehouses (id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_warehouse_stock_balances_spare_part'
    ) THEN
        ALTER TABLE warehouse_stock_balances
            ADD CONSTRAINT fk_warehouse_stock_balances_spare_part
                FOREIGN KEY (spare_part_id) REFERENCES spare_parts (id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_warehouse_stock_balances_bin'
    ) THEN
        ALTER TABLE warehouse_stock_balances
            ADD CONSTRAINT fk_warehouse_stock_balances_bin
                FOREIGN KEY (bin_id) REFERENCES warehouse_bins (id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_warehouse_stock_balance_qty'
    ) THEN
        ALTER TABLE warehouse_stock_balances
            ADD CONSTRAINT chk_warehouse_stock_balance_qty
                CHECK (
                    qty_on_hand >= 0
                    AND qty_reserved >= 0
                    AND qty_reserved <= qty_on_hand
                );
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_warehouse_stock_balances_identity_key
    ON warehouse_stock_balances (identity_key)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_warehouse_stock_balances_lookup
    ON warehouse_stock_balances (warehouse_id, spare_part_id, bin_id, lot_number, serial_number)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_warehouse_stock_balances_spare_part
    ON warehouse_stock_balances (spare_part_id)
    WHERE is_deleted = false;

INSERT INTO warehouse_stock_balances (
    id,
    created_at,
    updated_at,
    is_deleted,
    warehouse_id,
    spare_part_id,
    bin_id,
    lot_number,
    serial_number,
    identity_key,
    expiry_date,
    qty_on_hand,
    qty_reserved,
    avg_cost,
    version
)
SELECT
    gen_random_uuid(),
    COALESCE(ws.created_at, now()),
    COALESCE(ws.updated_at, now()),
    false,
    ws.warehouse_id,
    ws.spare_part_id,
    NULL,
    NULL,
    NULL,
    ws.warehouse_id::text || '|' || ws.spare_part_id::text || '|0||',
    NULL,
    GREATEST(COALESCE(ws.quantity, 0), 0)::numeric(19,4),
    LEAST(
        GREATEST(COALESCE(ws.reserved_qty, 0), 0),
        GREATEST(COALESCE(ws.quantity, 0), 0)
    )::numeric(19,4),
    NULL,
    0
FROM warehouse_stocks ws
WHERE COALESCE(ws.is_deleted, false) = false
  AND ws.warehouse_id IS NOT NULL
  AND ws.spare_part_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM warehouse_stock_balances b
      WHERE b.identity_key = ws.warehouse_id::text || '|' || ws.spare_part_id::text || '|0||'
        AND b.is_deleted = false
  );

CREATE TABLE IF NOT EXISTS warehouse_stock_ledgers (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,

    warehouse_id uuid NOT NULL,
    spare_part_id uuid NOT NULL,
    bin_id uuid,
    lot_number varchar(100),
    serial_number varchar(128),
    movement_type varchar(64) NOT NULL,
    quantity numeric(19,4) NOT NULL,
    unit_cost numeric(19,4),
    total_cost numeric(19,2),
    reference_type varchar(100),
    reference_id uuid,
    reference_doc_no varchar(100),
    idempotency_key varchar(512),
    posted_at timestamptz NOT NULL,
    notes text
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_warehouse_stock_ledgers_warehouse'
    ) THEN
        ALTER TABLE warehouse_stock_ledgers
            ADD CONSTRAINT fk_warehouse_stock_ledgers_warehouse
                FOREIGN KEY (warehouse_id) REFERENCES warehouses (id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_warehouse_stock_ledgers_spare_part'
    ) THEN
        ALTER TABLE warehouse_stock_ledgers
            ADD CONSTRAINT fk_warehouse_stock_ledgers_spare_part
                FOREIGN KEY (spare_part_id) REFERENCES spare_parts (id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_warehouse_stock_ledgers_bin'
    ) THEN
        ALTER TABLE warehouse_stock_ledgers
            ADD CONSTRAINT fk_warehouse_stock_ledgers_bin
                FOREIGN KEY (bin_id) REFERENCES warehouse_bins (id);
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_warehouse_stock_ledgers_idempotency_key
    ON warehouse_stock_ledgers (idempotency_key)
    WHERE idempotency_key IS NOT NULL AND is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_warehouse_stock_ledgers_reference
    ON warehouse_stock_ledgers (reference_type, reference_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_warehouse_stock_ledgers_lookup
    ON warehouse_stock_ledgers (warehouse_id, spare_part_id, movement_type, posted_at)
    WHERE is_deleted = false;

CREATE TABLE IF NOT EXISTS warehouse_reservation_ledgers (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,

    warehouse_id uuid NOT NULL,
    spare_part_id uuid NOT NULL,
    bin_id uuid,
    lot_number varchar(100),
    serial_number varchar(128),
    movement_type varchar(64) NOT NULL,
    quantity numeric(19,4) NOT NULL,
    reference_type varchar(100),
    reference_id uuid,
    reference_doc_no varchar(100),
    idempotency_key varchar(512),
    posted_at timestamptz NOT NULL,
    notes text
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_warehouse_reservation_ledgers_warehouse'
    ) THEN
        ALTER TABLE warehouse_reservation_ledgers
            ADD CONSTRAINT fk_warehouse_reservation_ledgers_warehouse
                FOREIGN KEY (warehouse_id) REFERENCES warehouses (id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_warehouse_reservation_ledgers_spare_part'
    ) THEN
        ALTER TABLE warehouse_reservation_ledgers
            ADD CONSTRAINT fk_warehouse_reservation_ledgers_spare_part
                FOREIGN KEY (spare_part_id) REFERENCES spare_parts (id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_warehouse_reservation_ledgers_bin'
    ) THEN
        ALTER TABLE warehouse_reservation_ledgers
            ADD CONSTRAINT fk_warehouse_reservation_ledgers_bin
                FOREIGN KEY (bin_id) REFERENCES warehouse_bins (id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_warehouse_reservation_ledgers_reference
    ON warehouse_reservation_ledgers (reference_type, reference_id)
    WHERE is_deleted = false;

CREATE UNIQUE INDEX IF NOT EXISTS uq_warehouse_reservation_ledgers_idempotency_key
    ON warehouse_reservation_ledgers (idempotency_key)
    WHERE idempotency_key IS NOT NULL AND is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_warehouse_reservation_ledgers_lookup
    ON warehouse_reservation_ledgers (warehouse_id, spare_part_id, movement_type, posted_at)
    WHERE is_deleted = false;
