ALTER TABLE warehouse_bins
    ADD COLUMN IF NOT EXISTS quality_zone_type varchar(32) NOT NULL DEFAULT 'STORAGE',
    ADD COLUMN IF NOT EXISTS temperature_zone varchar(64),
    ADD COLUMN IF NOT EXISTS hazard_class varchar(64),
    ADD COLUMN IF NOT EXISTS allow_mixed_spare_parts boolean NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS allow_mixed_lots boolean NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS barcode varchar(128),
    ADD COLUMN IF NOT EXISTS qr_payload text;

CREATE INDEX IF NOT EXISTS idx_warehouse_bins_zone_type
    ON warehouse_bins (warehouse_id, quality_zone_type, active, blocked, frozen)
    WHERE is_deleted = false;

CREATE UNIQUE INDEX IF NOT EXISTS uq_warehouse_bins_barcode
    ON warehouse_bins (barcode)
    WHERE barcode IS NOT NULL AND is_deleted = false;

ALTER TABLE warehouse_stock_balances
    ADD COLUMN IF NOT EXISTS stock_status varchar(32) NOT NULL DEFAULT 'AVAILABLE',
    ADD COLUMN IF NOT EXISTS quality_hold_reason varchar(255),
    ADD COLUMN IF NOT EXISTS quality_checked_at timestamptz,
    ADD COLUMN IF NOT EXISTS quality_checked_by_id uuid;

ALTER TABLE warehouse_stock_ledgers
    ADD COLUMN IF NOT EXISTS expiry_date date,
    ADD COLUMN IF NOT EXISTS stock_status varchar(32) NOT NULL DEFAULT 'AVAILABLE';

ALTER TABLE warehouse_reservation_ledgers
    ADD COLUMN IF NOT EXISTS expiry_date date,
    ADD COLUMN IF NOT EXISTS stock_status varchar(32) NOT NULL DEFAULT 'AVAILABLE';

DROP INDEX IF EXISTS uq_warehouse_stock_balances_identity_key;

UPDATE warehouse_stock_balances
SET identity_key =
    warehouse_id::text || '|' ||
    spare_part_id::text || '|' ||
    COALESCE(bin_id::text, '0') || '|' ||
    COALESCE(NULLIF(upper(trim(lot_number)), ''), '') || '|' ||
    COALESCE(NULLIF(upper(trim(serial_number)), ''), '') || '|' ||
    COALESCE(expiry_date::text, '') || '|' ||
    COALESCE(NULLIF(upper(trim(stock_status)), ''), 'AVAILABLE')
WHERE identity_key IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_warehouse_stock_balances_identity_key
    ON warehouse_stock_balances (identity_key)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_warehouse_stock_balances_status
    ON warehouse_stock_balances (warehouse_id, spare_part_id, stock_status)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_warehouse_stock_balances_lookup_v2
    ON warehouse_stock_balances (
        warehouse_id,
        spare_part_id,
        bin_id,
        lot_number,
        serial_number,
        expiry_date,
        stock_status
    )
    WHERE is_deleted = false;

ALTER TABLE warehouse_stock_ledger_metadata
    ADD COLUMN IF NOT EXISTS bin_id uuid,
    ADD COLUMN IF NOT EXISTS from_bin_id uuid,
    ADD COLUMN IF NOT EXISTS to_bin_id uuid,
    ADD COLUMN IF NOT EXISTS source_bin_id uuid,
    ADD COLUMN IF NOT EXISTS destination_bin_id uuid,
    ADD COLUMN IF NOT EXISTS lot_number varchar(100),
    ADD COLUMN IF NOT EXISTS serial_number varchar(128),
    ADD COLUMN IF NOT EXISTS expiry_date date,
    ADD COLUMN IF NOT EXISTS stock_status varchar(32) NOT NULL DEFAULT 'AVAILABLE',
    ADD COLUMN IF NOT EXISTS source_document_no varchar(100),
    ADD COLUMN IF NOT EXISTS source_document_date date;

CREATE INDEX IF NOT EXISTS idx_stock_movement_metadata_coordinates
    ON warehouse_stock_ledger_metadata (warehouse_id, spare_part_id, bin_id, lot_number, serial_number, expiry_date, stock_status)
    WHERE is_deleted = false;

CREATE OR REPLACE VIEW stock_movements AS
SELECT m.id,
       m.created_at,
       COALESCE(l.posted_at, m.updated_at) AS updated_at,
       m.is_deleted,
       m.created_by_id,
       m.updated_by_id,
       m.warehouse_id,
       m.spare_part_id,
       m.equipment_type_id,
       m.work_order_id,
       COALESCE(
           CASE l.movement_type
               WHEN 'RECEIPT' THEN 'RECEIPT'
               WHEN 'RETURN' THEN 'RETURN'
               WHEN 'ISSUE' THEN 'ISSUE'
               WHEN 'WRITEOFF' THEN 'WRITEOFF'
               WHEN 'TRANSFER_IN' THEN 'TRANSFER'
               WHEN 'TRANSFER_OUT' THEN 'TRANSFER'
               WHEN 'MOVE_IN' THEN 'BIN_MOVE'
               WHEN 'MOVE_OUT' THEN 'BIN_MOVE'
               WHEN 'STATUS_TRANSFER_IN' THEN 'TRANSFER'
               WHEN 'STATUS_TRANSFER_OUT' THEN 'TRANSFER'
               WHEN 'ADJUSTMENT_INC' THEN 'ADJUSTMENT'
               WHEN 'ADJUSTMENT_DEC' THEN 'ADJUSTMENT'
               ELSE NULL
           END,
           m.legacy_type
       )::varchar(64) AS type,
       COALESCE(ABS(l.quantity)::double precision, m.submitted_quantity) AS quantity,
       m.unit,
       COALESCE(l.unit_cost::double precision, m.submitted_unit_cost) AS unit_cost,
       COALESCE(m.unit_price, l.unit_cost::numeric(19,2)) AS unit_price,
       COALESCE(ABS(l.total_cost), m.submitted_total_amount) AS total_amount,
       COALESCE(l.reference_doc_no, m.document_number) AS document_number,
       m.source_type,
       m.source_id,
       m.source_line_id,
       m.responsible_person_id,
       m.taken_by_id,
       m.department_id,
       m.supplier_name,
       m.movement_date,
       COALESCE(l.posted_at, m.submitted_occurred_at) AS occurred_at,
       COALESCE(l.notes, m.notes) AS notes,
       COALESCE(m.comment, l.notes) AS comment,
       COALESCE(l.bin_id, m.bin_id) AS bin_id,
       m.from_bin_id,
       m.to_bin_id,
       m.source_bin_id,
       m.destination_bin_id,
       COALESCE(l.lot_number, m.lot_number) AS lot_number,
       COALESCE(l.serial_number, m.serial_number) AS serial_number,
       COALESCE(l.expiry_date, m.expiry_date) AS expiry_date,
       COALESCE(l.stock_status, m.stock_status, 'AVAILABLE') AS stock_status,
       m.source_document_no,
       m.source_document_date
FROM warehouse_stock_ledger_metadata m
LEFT JOIN LATERAL (
    SELECT ledger.*
    FROM warehouse_stock_ledgers ledger
    WHERE ledger.is_deleted = false
      AND (
          ledger.idempotency_key = 'legacy-stock-movement:' || m.id::text
          OR ledger.idempotency_key LIKE '%:' || m.id::text
      )
    ORDER BY ledger.posted_at DESC
    LIMIT 1
) l ON true;

CREATE OR REPLACE FUNCTION insert_stock_movement_metadata()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO warehouse_stock_ledger_metadata (
        id, created_at, updated_at, is_deleted, created_by_id, updated_by_id,
        warehouse_id, spare_part_id, equipment_type_id, work_order_id,
        legacy_type, submitted_quantity, unit, submitted_unit_cost, unit_price,
        submitted_total_amount, document_number, source_type, source_id, source_line_id,
        responsible_person_id, taken_by_id, department_id, supplier_name,
        movement_date, submitted_occurred_at, notes, comment,
        bin_id, from_bin_id, to_bin_id, source_bin_id, destination_bin_id,
        lot_number, serial_number, expiry_date, stock_status,
        source_document_no, source_document_date
    )
    VALUES (
        NEW.id, COALESCE(NEW.created_at, now()), COALESCE(NEW.updated_at, now()),
        COALESCE(NEW.is_deleted, false), NEW.created_by_id, NEW.updated_by_id,
        NEW.warehouse_id, NEW.spare_part_id, NEW.equipment_type_id, NEW.work_order_id,
        NEW.type, NEW.quantity, NEW.unit, NEW.unit_cost, NEW.unit_price,
        NEW.total_amount, NEW.document_number, NEW.source_type, NEW.source_id,
        NEW.source_line_id, NEW.responsible_person_id, NEW.taken_by_id,
        NEW.department_id, NEW.supplier_name, NEW.movement_date,
        COALESCE(NEW.occurred_at, now()), NEW.notes, NEW.comment,
        NEW.bin_id, NEW.from_bin_id, NEW.to_bin_id, NEW.source_bin_id, NEW.destination_bin_id,
        NEW.lot_number, NEW.serial_number, NEW.expiry_date, COALESCE(NEW.stock_status, 'AVAILABLE'),
        NEW.source_document_no, NEW.source_document_date
    );
    RETURN NEW;
END;
$$;

ALTER TABLE inventory_transactions
    ADD COLUMN IF NOT EXISTS bin_id uuid,
    ADD COLUMN IF NOT EXISTS source_bin_id uuid,
    ADD COLUMN IF NOT EXISTS destination_bin_id uuid,
    ADD COLUMN IF NOT EXISTS lot_number varchar(100),
    ADD COLUMN IF NOT EXISTS serial_number varchar(128),
    ADD COLUMN IF NOT EXISTS expiry_date date,
    ADD COLUMN IF NOT EXISTS stock_status varchar(32) NOT NULL DEFAULT 'AVAILABLE',
    ADD COLUMN IF NOT EXISTS source_type varchar(64),
    ADD COLUMN IF NOT EXISTS source_id uuid,
    ADD COLUMN IF NOT EXISTS source_document_no varchar(100);

CREATE INDEX IF NOT EXISTS idx_inventory_tx_coordinates
    ON inventory_transactions (warehouse_id, spare_part_id, bin_id, lot_number, serial_number, expiry_date, stock_status);

CREATE INDEX IF NOT EXISTS idx_inventory_tx_source
    ON inventory_transactions (source_type, source_id);

ALTER TABLE reservations
    ALTER COLUMN warehouse_stock_id DROP NOT NULL,
    ADD COLUMN IF NOT EXISTS requirement_id uuid,
    ADD COLUMN IF NOT EXISTS lot_number varchar(100),
    ADD COLUMN IF NOT EXISTS serial_number varchar(128),
    ADD COLUMN IF NOT EXISTS expiry_date date,
    ADD COLUMN IF NOT EXISTS stock_status varchar(32) NOT NULL DEFAULT 'AVAILABLE';

CREATE INDEX IF NOT EXISTS idx_reservations_stock_identity
    ON reservations (warehouse_id, spare_part_id, bin_id, lot_number, serial_number, expiry_date, stock_status)
    WHERE is_deleted = false;

ALTER TABLE repair_material_usages
    ADD COLUMN IF NOT EXISTS bin_id uuid,
    ADD COLUMN IF NOT EXISTS lot_number varchar(100),
    ADD COLUMN IF NOT EXISTS serial_number varchar(128),
    ADD COLUMN IF NOT EXISTS expiry_date date,
    ADD COLUMN IF NOT EXISTS stock_status varchar(32) NOT NULL DEFAULT 'AVAILABLE',
    ADD COLUMN IF NOT EXISTS source_document_no varchar(100);

CREATE INDEX IF NOT EXISTS idx_repair_material_usages_stock_identity
    ON repair_material_usages (bin_id, lot_number, serial_number, expiry_date, stock_status)
    WHERE is_deleted = false;

ALTER TABLE warehouse_equipment_items
    ADD COLUMN IF NOT EXISTS bin_id uuid,
    ADD COLUMN IF NOT EXISTS lot_number varchar(100),
    ADD COLUMN IF NOT EXISTS serial_number varchar(128),
    ADD COLUMN IF NOT EXISTS barcode varchar(128),
    ADD COLUMN IF NOT EXISTS qr_payload text,
    ADD COLUMN IF NOT EXISTS receipt_document_no varchar(100);

CREATE UNIQUE INDEX IF NOT EXISTS uq_warehouse_equipment_items_barcode
    ON warehouse_equipment_items (barcode)
    WHERE barcode IS NOT NULL AND is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_warehouse_equipment_items_bin
    ON warehouse_equipment_items (warehouse_id, bin_id)
    WHERE is_deleted = false;

CREATE TABLE IF NOT EXISTS warehouse_tasks (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    task_number varchar(64) NOT NULL,
    task_type varchar(32) NOT NULL,
    status varchar(32) NOT NULL,
    priority varchar(32) NOT NULL DEFAULT 'NORMAL',
    warehouse_id uuid NOT NULL REFERENCES warehouses (id),
    source_type varchar(64) NOT NULL,
    source_id uuid,
    assigned_to_id uuid,
    due_at timestamptz,
    started_at timestamptz,
    completed_at timestamptz,
    cancelled_at timestamptz,
    comment text
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_warehouse_tasks_task_number
    ON warehouse_tasks (task_number)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_warehouse_tasks_queue
    ON warehouse_tasks (warehouse_id, task_type, status, priority, due_at)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_warehouse_tasks_status
    ON warehouse_tasks (warehouse_id, status, updated_at)
    WHERE is_deleted = false;

CREATE TABLE IF NOT EXISTS warehouse_task_lines (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    task_id uuid NOT NULL REFERENCES warehouse_tasks (id),
    spare_part_id uuid,
    equipment_id uuid,
    from_bin_id uuid REFERENCES warehouse_bins (id),
    to_bin_id uuid REFERENCES warehouse_bins (id),
    lot_number varchar(100),
    serial_number varchar(128),
    expiry_date date,
    stock_status varchar(32) NOT NULL DEFAULT 'AVAILABLE',
    planned_qty numeric(19,4) NOT NULL DEFAULT 0,
    actual_qty numeric(19,4) NOT NULL DEFAULT 0,
    unit varchar(64),
    status varchar(32) NOT NULL,
    scan_confirmed boolean NOT NULL DEFAULT false,
    exception_reason text
);

CREATE INDEX IF NOT EXISTS idx_warehouse_task_lines_task
    ON warehouse_task_lines (task_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_warehouse_task_lines_status
    ON warehouse_task_lines (task_id, status)
    WHERE is_deleted = false;

CREATE TABLE IF NOT EXISTS inventory_count_sessions (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    session_number varchar(64) NOT NULL,
    warehouse_id uuid NOT NULL REFERENCES warehouses (id),
    status varchar(32) NOT NULL,
    scope_type varchar(32) NOT NULL,
    scope_zone varchar(64),
    scope_bin_id uuid REFERENCES warehouse_bins (id),
    scope_spare_part_id uuid REFERENCES spare_parts (id),
    scope_abc_class varchar(1),
    random_sample_size integer,
    blind_count boolean NOT NULL DEFAULT false,
    created_by_id uuid,
    approved_by_id uuid,
    opened_at timestamptz,
    closed_at timestamptz,
    posted_at timestamptz,
    document_number varchar(100),
    comment text
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_inventory_count_sessions_number
    ON inventory_count_sessions (session_number)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_inventory_count_sessions_status
    ON inventory_count_sessions (warehouse_id, status, updated_at)
    WHERE is_deleted = false;

CREATE TABLE IF NOT EXISTS inventory_count_lines (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    session_id uuid NOT NULL REFERENCES inventory_count_sessions (id),
    warehouse_id uuid NOT NULL REFERENCES warehouses (id),
    bin_id uuid REFERENCES warehouse_bins (id),
    spare_part_id uuid NOT NULL REFERENCES spare_parts (id),
    lot_number varchar(100),
    serial_number varchar(128),
    expiry_date date,
    stock_status varchar(32) NOT NULL DEFAULT 'AVAILABLE',
    expected_qty numeric(19,4) NOT NULL DEFAULT 0,
    counted_qty numeric(19,4),
    variance_qty numeric(19,4),
    unit varchar(64),
    status varchar(32) NOT NULL,
    counted_by_id uuid,
    counted_at timestamptz,
    variance_reason text
);

CREATE INDEX IF NOT EXISTS idx_inventory_count_lines_session
    ON inventory_count_lines (session_id, status)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_inventory_count_lines_identity
    ON inventory_count_lines (warehouse_id, bin_id, spare_part_id, lot_number, serial_number, expiry_date, stock_status)
    WHERE is_deleted = false;

CREATE TABLE IF NOT EXISTS repair_material_returns (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    work_order_id uuid NOT NULL REFERENCES work_orders (id),
    material_usage_id uuid REFERENCES repair_material_usages (id),
    warehouse_id uuid NOT NULL REFERENCES warehouses (id),
    spare_part_id uuid NOT NULL REFERENCES spare_parts (id),
    bin_id uuid REFERENCES warehouse_bins (id),
    lot_number varchar(100),
    serial_number varchar(128),
    expiry_date date,
    stock_status varchar(32) NOT NULL,
    quantity numeric(19,4) NOT NULL,
    reason text NOT NULL,
    returned_by_id uuid,
    responsible_person_id uuid,
    stock_movement_id uuid REFERENCES warehouse_stock_ledger_metadata (id),
    inventory_transaction_id uuid,
    status varchar(32) NOT NULL DEFAULT 'POSTED'
);

CREATE INDEX IF NOT EXISTS idx_repair_material_returns_work_order
    ON repair_material_returns (work_order_id, status)
    WHERE is_deleted = false;

CREATE TABLE IF NOT EXISTS warehouse_writeoff_requests (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    request_number varchar(64) NOT NULL,
    warehouse_id uuid NOT NULL REFERENCES warehouses (id),
    spare_part_id uuid NOT NULL REFERENCES spare_parts (id),
    bin_id uuid REFERENCES warehouse_bins (id),
    lot_number varchar(100),
    serial_number varchar(128),
    expiry_date date,
    stock_status varchar(32) NOT NULL DEFAULT 'WRITEOFF_PENDING',
    quantity numeric(19,4) NOT NULL,
    reason text NOT NULL,
    status varchar(32) NOT NULL,
    requested_by_id uuid,
    approved_by_id uuid,
    approval_request_id uuid REFERENCES approval_requests (id),
    stock_movement_id uuid REFERENCES warehouse_stock_ledger_metadata (id),
    document_number varchar(100),
    comment text
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_warehouse_writeoff_request_number
    ON warehouse_writeoff_requests (request_number)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_warehouse_writeoff_requests_status
    ON warehouse_writeoff_requests (warehouse_id, status, updated_at)
    WHERE is_deleted = false;

ALTER TABLE attachment_groups
    DROP CONSTRAINT IF EXISTS attachment_target_type_check;

ALTER TABLE attachment_groups
    ADD CONSTRAINT attachment_target_type_check
    CHECK (target_type IN (
        'EQUIPMENT',
        'VEHICLE',
        'WORK_ORDER',
        'REPAIR_REQUEST',
        'DEFECT',
        'COMPLETION_ACT',
        'APPROVAL',
        'PROCUREMENT_REQUEST',
        'PURCHASE_ORDER',
        'STOCK_MOVEMENT',
        'EQUIPMENT_COMMISSIONING',
        'INVENTORY_COUNT_SESSION',
        'WAREHOUSE_TASK',
        'WAREHOUSE_BIN',
        'WAREHOUSE_WRITEOFF',
        'HR_EMPLOYEE'
    ));

ALTER TABLE work_order_spare_part_requirements
    DROP CONSTRAINT IF EXISTS chk_wo_spare_req_status;

ALTER TABLE work_order_spare_part_requirements
    ADD CONSTRAINT chk_wo_spare_req_status
    CHECK (status IN (
        'PLANNED',
        'CANCELLED',
        'PARTIALLY_RESERVED',
        'RESERVED',
        'PARTIALLY_ISSUED',
        'ISSUED'
    ));

CREATE OR REPLACE FUNCTION append_wms_backend_foundation_role_permissions(role_code text, permissions_to_add text[])
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE roles r
    SET permissions = (
            SELECT COALESCE(jsonb_agg(DISTINCT permission ORDER BY permission), '[]'::jsonb)
            FROM (
                SELECT jsonb_array_elements_text(
                        CASE
                            WHEN jsonb_typeof(COALESCE(r.permissions, '[]'::jsonb)) = 'array'
                                THEN COALESCE(r.permissions, '[]'::jsonb)
                            ELSE '[]'::jsonb
                        END
                    ) AS permission
                UNION ALL
                SELECT unnest(permissions_to_add) AS permission
            ) merged_permissions
            WHERE permission IS NOT NULL AND btrim(permission) <> ''
        ),
        updated_at = now()
    WHERE r.code = role_code
      AND r.is_deleted = false;
END $$;

SELECT append_wms_backend_foundation_role_permissions('SYSTEM_ADMIN', ARRAY['*']);

SELECT append_wms_backend_foundation_role_permissions('STOREKEEPER', ARRAY[
    'WAREHOUSE_BIN_READ',
    'WAREHOUSE_BIN_MANAGE',
    'WAREHOUSE_TASK_READ',
    'WAREHOUSE_TASK_ASSIGN',
    'WAREHOUSE_TASK_EXECUTE',
    'WAREHOUSE_RECEIVE',
    'WAREHOUSE_PUTAWAY',
    'WAREHOUSE_PICK',
    'WAREHOUSE_COUNT_CREATE',
    'WAREHOUSE_COUNT_EXECUTE',
    'WAREHOUSE_WRITEOFF_REQUEST',
    'WAREHOUSE_DOCUMENT_UPLOAD'
]);

SELECT append_wms_backend_foundation_role_permissions('SUPPLY_SPECIALIST', ARRAY[
    'WAREHOUSE_BIN_READ',
    'WAREHOUSE_TASK_READ',
    'WAREHOUSE_RECEIVE',
    'WAREHOUSE_DOCUMENT_UPLOAD'
]);

SELECT append_wms_backend_foundation_role_permissions('VIEWER', ARRAY[
    'WAREHOUSE_BIN_READ',
    'WAREHOUSE_TASK_READ'
]);

DROP FUNCTION append_wms_backend_foundation_role_permissions(text, text[]);
