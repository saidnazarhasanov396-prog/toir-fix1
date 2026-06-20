-- Retire physical legacy movement history.
-- Descriptive fields remain in a metadata table for API compatibility.
-- Quantity, cost and posting time are sourced from the WMS ledger when linked.

CREATE TABLE warehouse_stock_ledger_metadata (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,
    created_by_id uuid,
    updated_by_id uuid,
    warehouse_id uuid NOT NULL,
    spare_part_id uuid,
    equipment_type_id uuid,
    work_order_id uuid,
    legacy_type varchar(64) NOT NULL,
    submitted_quantity double precision NOT NULL,
    unit varchar(100),
    submitted_unit_cost double precision,
    unit_price numeric(19,2),
    submitted_total_amount numeric(19,2),
    document_number varchar(255),
    source_type varchar(64),
    source_id uuid,
    source_line_id uuid,
    responsible_person_id uuid,
    taken_by_id uuid,
    department_id uuid,
    supplier_name varchar(255),
    movement_date date,
    submitted_occurred_at timestamptz NOT NULL,
    notes text,
    comment text
);

INSERT INTO warehouse_stock_ledger_metadata (
    id, created_at, updated_at, is_deleted, created_by_id, updated_by_id,
    warehouse_id, spare_part_id, equipment_type_id, work_order_id,
    legacy_type, submitted_quantity, unit, submitted_unit_cost, unit_price,
    submitted_total_amount, document_number, source_type, source_id, source_line_id,
    responsible_person_id, taken_by_id, department_id, supplier_name,
    movement_date, submitted_occurred_at, notes, comment
)
SELECT id, created_at, updated_at, is_deleted, created_by_id, updated_by_id,
       warehouse_id, spare_part_id, equipment_type_id, work_order_id,
       type, quantity, unit, unit_cost, unit_price, total_amount, document_number,
       source_type, source_id, source_line_id, responsible_person_id, taken_by_id,
       department_id, supplier_name, movement_date, occurred_at, notes, comment
FROM stock_movements;

DO $$
DECLARE
    fk record;
BEGIN
    FOR fk IN
        SELECT conrelid::regclass AS table_name, conname
        FROM pg_constraint
        WHERE contype = 'f'
          AND confrelid = 'stock_movements'::regclass
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', fk.table_name, fk.conname);
    END LOOP;
END
$$;

ALTER TABLE repair_material_usages
    ADD CONSTRAINT fk_repair_material_usage_movement_metadata
        FOREIGN KEY (stock_movement_id) REFERENCES warehouse_stock_ledger_metadata(id);

ALTER TABLE stock_movement_files
    ADD CONSTRAINT fk_stock_movement_files_metadata
        FOREIGN KEY (stock_movement_id) REFERENCES warehouse_stock_ledger_metadata(id)
        ON DELETE CASCADE;

DROP TRIGGER IF EXISTS trg_guard_legacy_stock_movement_mutation ON stock_movements;
DROP FUNCTION IF EXISTS guard_legacy_stock_movement_mutation();
DROP TABLE stock_movements;

CREATE VIEW stock_movements AS
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
               WHEN 'WRITEOFF' THEN 'ISSUE'
               WHEN 'TRANSFER_IN' THEN 'TRANSFER'
               WHEN 'TRANSFER_OUT' THEN 'TRANSFER'
               WHEN 'MOVE_IN' THEN 'TRANSFER'
               WHEN 'MOVE_OUT' THEN 'TRANSFER'
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
       COALESCE(m.comment, l.notes) AS comment
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
        movement_date, submitted_occurred_at, notes, comment
    )
    VALUES (
        NEW.id, COALESCE(NEW.created_at, now()), COALESCE(NEW.updated_at, now()),
        COALESCE(NEW.is_deleted, false), NEW.created_by_id, NEW.updated_by_id,
        NEW.warehouse_id, NEW.spare_part_id, NEW.equipment_type_id, NEW.work_order_id,
        NEW.type, NEW.quantity, NEW.unit, NEW.unit_cost, NEW.unit_price,
        NEW.total_amount, NEW.document_number, NEW.source_type, NEW.source_id,
        NEW.source_line_id, NEW.responsible_person_id, NEW.taken_by_id,
        NEW.department_id, NEW.supplier_name, NEW.movement_date,
        COALESCE(NEW.occurred_at, now()), NEW.notes, NEW.comment
    );
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_insert_stock_movement_metadata
INSTEAD OF INSERT
ON stock_movements
FOR EACH ROW
EXECUTE FUNCTION insert_stock_movement_metadata();

CREATE OR REPLACE FUNCTION reject_stock_movement_view_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION
        'stock_movements is an append-only WMS compatibility view';
END;
$$;

CREATE TRIGGER trg_reject_stock_movement_view_mutation
INSTEAD OF UPDATE OR DELETE
ON stock_movements
FOR EACH ROW
EXECUTE FUNCTION reject_stock_movement_view_mutation();

COMMENT ON VIEW stock_movements IS
    'Deprecated compatibility view. Warehouse stock ledger is the physical history source.';
