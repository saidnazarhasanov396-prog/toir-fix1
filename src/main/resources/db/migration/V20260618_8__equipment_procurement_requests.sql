ALTER TABLE procurement_requests
    ADD COLUMN IF NOT EXISTS type varchar(32) NOT NULL DEFAULT 'SPARE_PART',
    ADD COLUMN IF NOT EXISTS source_defect_id uuid,
    ADD COLUMN IF NOT EXISTS source_defect_title varchar(255),
    ADD COLUMN IF NOT EXISTS source_ppr_task_id uuid,
    ADD COLUMN IF NOT EXISTS source_ppr_task_title varchar(255);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'procurement_requests_type_check'
          AND conrelid = 'procurement_requests'::regclass
    ) THEN
        ALTER TABLE procurement_requests DROP CONSTRAINT procurement_requests_type_check;
    END IF;
END $$;

ALTER TABLE procurement_requests
    ADD CONSTRAINT procurement_requests_type_check
    CHECK (type IN ('SPARE_PART', 'EQUIPMENT'));

CREATE INDEX IF NOT EXISTS idx_procurement_requests_type
    ON procurement_requests (type)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_procurement_requests_source_defect
    ON procurement_requests (source_defect_id)
    WHERE is_deleted = false AND source_defect_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_procurement_requests_source_ppr_task
    ON procurement_requests (source_ppr_task_id)
    WHERE is_deleted = false AND source_ppr_task_id IS NOT NULL;

ALTER TABLE procurement_request_lines
    ALTER COLUMN spare_part_id DROP NOT NULL,
    ADD COLUMN IF NOT EXISTS equipment_type_id uuid,
    ADD COLUMN IF NOT EXISTS equipment_type_name varchar(255);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_procurement_request_lines_item_xor'
          AND conrelid = 'procurement_request_lines'::regclass
    ) THEN
        ALTER TABLE procurement_request_lines DROP CONSTRAINT chk_procurement_request_lines_item_xor;
    END IF;
END $$;

ALTER TABLE procurement_request_lines
    ADD CONSTRAINT chk_procurement_request_lines_item_xor
    CHECK (
        (spare_part_id IS NOT NULL AND equipment_type_id IS NULL)
        OR (spare_part_id IS NULL AND equipment_type_id IS NOT NULL)
    );

CREATE INDEX IF NOT EXISTS idx_procurement_request_lines_equipment_type_id
    ON procurement_request_lines (equipment_type_id)
    WHERE is_deleted = false AND equipment_type_id IS NOT NULL;

ALTER TABLE stock_movements
    ALTER COLUMN spare_part_id DROP NOT NULL,
    ADD COLUMN IF NOT EXISTS equipment_type_id uuid;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'stock_movements_type_check'
          AND conrelid = 'stock_movements'::regclass
    ) THEN
        ALTER TABLE stock_movements DROP CONSTRAINT stock_movements_type_check;
    END IF;
END $$;

ALTER TABLE stock_movements
    ADD CONSTRAINT stock_movements_type_check
    CHECK (type IN (
        'RECEIPT',
        'ISSUE',
        'TRANSFER',
        'RESERVATION',
        'RELEASE',
        'ADJUSTMENT',
        'RETURN',
        'EQUIPMENT_IN'
    ));

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_stock_movements_item_xor'
          AND conrelid = 'stock_movements'::regclass
    ) THEN
        ALTER TABLE stock_movements DROP CONSTRAINT chk_stock_movements_item_xor;
    END IF;
END $$;

ALTER TABLE stock_movements
    ADD CONSTRAINT chk_stock_movements_item_xor
    CHECK (
        (spare_part_id IS NOT NULL AND equipment_type_id IS NULL)
        OR (spare_part_id IS NULL AND equipment_type_id IS NOT NULL)
    );

CREATE INDEX IF NOT EXISTS idx_stock_movements_equipment_type_id
    ON stock_movements (equipment_type_id)
    WHERE is_deleted = false AND equipment_type_id IS NOT NULL;

ALTER TABLE equipment
    ADD COLUMN IF NOT EXISTS procurement_request_id uuid,
    ADD COLUMN IF NOT EXISTS procurement_request_line_id uuid,
    ADD COLUMN IF NOT EXISTS procurement_stock_movement_id uuid;

CREATE INDEX IF NOT EXISTS idx_equipment_procurement_request
    ON equipment (procurement_request_id)
    WHERE is_deleted = false AND procurement_request_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_equipment_procurement_line
    ON equipment (procurement_request_line_id)
    WHERE is_deleted = false AND procurement_request_line_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_equipment_procurement_stock_movement
    ON equipment (procurement_stock_movement_id)
    WHERE is_deleted = false AND procurement_stock_movement_id IS NOT NULL;
