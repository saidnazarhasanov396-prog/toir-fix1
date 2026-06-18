ALTER TABLE procurement_request_lines
    ADD COLUMN IF NOT EXISTS received_quantity double precision NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS remaining_quantity double precision;

UPDATE procurement_request_lines
SET remaining_quantity = GREATEST(quantity - received_quantity, 0)
WHERE remaining_quantity IS NULL;

ALTER TABLE procurement_request_lines
    ALTER COLUMN remaining_quantity SET NOT NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'procurement_requests_status_check'
          AND conrelid = 'procurement_requests'::regclass
    ) THEN
        ALTER TABLE procurement_requests DROP CONSTRAINT procurement_requests_status_check;
    END IF;
END $$;

ALTER TABLE procurement_requests
    ADD CONSTRAINT procurement_requests_status_check
    CHECK (status IN (
        'DRAFT',
        'SUBMITTED',
        'APPROVED',
        'ORDERED',
        'PARTIALLY_RECEIVED',
        'RECEIVED',
        'CANCELLED',
        'REJECTED'
    ));

ALTER TABLE stock_movements
    ADD COLUMN IF NOT EXISTS source_type varchar(64),
    ADD COLUMN IF NOT EXISTS source_id uuid,
    ADD COLUMN IF NOT EXISTS source_line_id uuid;

CREATE INDEX IF NOT EXISTS idx_stock_movements_source
    ON stock_movements (source_type, source_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_stock_movements_source_line
    ON stock_movements (source_line_id)
    WHERE is_deleted = false;

UPDATE stock_movements
SET source_type = 'PROCUREMENT_REQUEST',
    source_id = substring(notes from 'Procurement receipt: ([0-9a-fA-F-]{36})')::uuid
WHERE source_type IS NULL
  AND notes ~ '^Procurement receipt: [0-9a-fA-F-]{36}$';

UPDATE stock_movements
SET source_type = 'PURCHASE_ORDER'
WHERE source_type IS NULL
  AND notes LIKE 'Purchase order receipt:%';
