CREATE TABLE IF NOT EXISTS warehouse_writeoff_allocations (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    writeoff_request_id uuid NOT NULL REFERENCES warehouse_writeoff_requests (id),
    source_balance_id uuid REFERENCES warehouse_stock_balances (id),
    warehouse_id uuid NOT NULL REFERENCES warehouses (id),
    spare_part_id uuid NOT NULL REFERENCES spare_parts (id),
    bin_id uuid REFERENCES warehouse_bins (id),
    lot_number varchar(100),
    serial_number varchar(128),
    expiry_date date,
    source_stock_status varchar(32) NOT NULL,
    quantity numeric(19,4) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_warehouse_writeoff_allocations_request
    ON warehouse_writeoff_allocations (writeoff_request_id, created_at)
    WHERE is_deleted = false;
