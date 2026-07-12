DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM stock_movements WHERE quantity::text IN ('NaN','Infinity','-Infinity') OR abs(quantity) >= 1000000000000000)
       OR EXISTS (SELECT 1 FROM repair_material_usages WHERE quantity::text IN ('NaN','Infinity','-Infinity') OR abs(quantity) >= 1000000000000000)
       OR EXISTS (SELECT 1 FROM repair_material_returns WHERE quantity::text IN ('NaN','Infinity','-Infinity') OR abs(quantity) >= 1000000000000000)
       OR EXISTS (SELECT 1 FROM maintenance_template_spare_part_requirements WHERE quantity::text IN ('NaN','Infinity','-Infinity') OR abs(quantity) >= 1000000000000000)
       OR EXISTS (SELECT 1 FROM maintenance_regulation_spare_part_requirements WHERE quantity::text IN ('NaN','Infinity','-Infinity') OR abs(quantity) >= 1000000000000000)
       OR EXISTS (SELECT 1 FROM actual_costs WHERE amount::text IN ('NaN','Infinity','-Infinity') OR abs(amount) >= 1000000000000000) THEN
        RAISE EXCEPTION 'RC_V5_1_NUMERIC_OVERFLOW_REMEDIATION_REQUIRED';
    END IF;
END $$;

DROP VIEW stock_movements;
ALTER TABLE warehouse_stock_ledger_metadata ALTER COLUMN submitted_quantity TYPE numeric(19,4) USING round(submitted_quantity::numeric,4);
ALTER TABLE repair_material_usages ALTER COLUMN quantity TYPE numeric(19,4) USING round(quantity::numeric,4);
ALTER TABLE repair_material_returns ALTER COLUMN quantity TYPE numeric(19,4) USING round(quantity::numeric,4);
ALTER TABLE maintenance_template_spare_part_requirements ALTER COLUMN quantity TYPE numeric(19,4) USING round(quantity::numeric,4);
ALTER TABLE maintenance_regulation_spare_part_requirements ALTER COLUMN quantity TYPE numeric(19,4) USING round(quantity::numeric,4);
ALTER TABLE actual_costs ALTER COLUMN amount TYPE numeric(19,4) USING round(amount::numeric,4);

CREATE VIEW stock_movements AS
SELECT m.id,m.created_at,COALESCE(l.posted_at,m.updated_at) AS updated_at,m.is_deleted,m.created_by_id,m.updated_by_id,
       m.warehouse_id,m.spare_part_id,m.equipment_type_id,m.work_order_id,
       COALESCE(CASE l.movement_type WHEN 'RECEIPT' THEN 'RECEIPT' WHEN 'RETURN' THEN 'RETURN' WHEN 'ISSUE' THEN 'ISSUE'
           WHEN 'WRITEOFF' THEN 'WRITEOFF' WHEN 'TRANSFER_IN' THEN 'TRANSFER' WHEN 'TRANSFER_OUT' THEN 'TRANSFER'
           WHEN 'MOVE_IN' THEN 'BIN_MOVE' WHEN 'MOVE_OUT' THEN 'BIN_MOVE' WHEN 'STATUS_TRANSFER_IN' THEN 'TRANSFER'
           WHEN 'STATUS_TRANSFER_OUT' THEN 'TRANSFER' WHEN 'ADJUSTMENT_INC' THEN 'ADJUSTMENT'
           WHEN 'ADJUSTMENT_DEC' THEN 'ADJUSTMENT' ELSE NULL END,m.legacy_type)::varchar(64) AS type,
       COALESCE(ABS(l.quantity),m.submitted_quantity)::numeric(19,4) AS quantity,m.unit,
       COALESCE(l.unit_cost::double precision,m.submitted_unit_cost) AS unit_cost,
       COALESCE(m.unit_price,l.unit_cost::numeric(19,2)) AS unit_price,
       COALESCE(ABS(l.total_cost),m.submitted_total_amount) AS total_amount,
       COALESCE(l.reference_doc_no,m.document_number) AS document_number,m.source_type,m.source_id,m.source_line_id,
       m.responsible_person_id,m.taken_by_id,m.department_id,m.supplier_name,m.movement_date,
       COALESCE(l.posted_at,m.submitted_occurred_at) AS occurred_at,COALESCE(l.notes,m.notes) AS notes,
       COALESCE(m.comment,l.notes) AS comment,COALESCE(l.bin_id,m.bin_id) AS bin_id,m.from_bin_id,m.to_bin_id,
       m.source_bin_id,m.destination_bin_id,COALESCE(l.lot_number,m.lot_number) AS lot_number,
       COALESCE(l.serial_number,m.serial_number) AS serial_number,COALESCE(l.expiry_date,m.expiry_date) AS expiry_date,
       COALESCE(l.stock_status,m.stock_status,'AVAILABLE') AS stock_status,m.source_document_no,m.source_document_date
FROM warehouse_stock_ledger_metadata m
LEFT JOIN LATERAL (SELECT ledger.* FROM warehouse_stock_ledgers ledger WHERE ledger.is_deleted=false AND
    (ledger.idempotency_key='legacy-stock-movement:'||m.id::text OR ledger.idempotency_key LIKE '%:'||m.id::text)
    ORDER BY ledger.posted_at DESC LIMIT 1) l ON true;

CREATE TRIGGER trg_insert_stock_movement_metadata INSTEAD OF INSERT ON stock_movements
FOR EACH ROW EXECUTE FUNCTION insert_stock_movement_metadata();

CREATE OR REPLACE FUNCTION reject_stock_movement_view_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'STOCK_MOVEMENTS_APPEND_ONLY: stock_movements is an append-only WMS compatibility view';
END $$ LANGUAGE plpgsql;
CREATE TRIGGER trg_reject_stock_movement_view_mutation
    INSTEAD OF UPDATE OR DELETE ON stock_movements
    FOR EACH ROW EXECUTE FUNCTION reject_stock_movement_view_mutation();
COMMENT ON VIEW stock_movements IS
    'Deprecated compatibility view. Warehouse stock ledger is the physical history source.';

ALTER TABLE work_order_spare_part_requirements ADD COLUMN warehouse_id uuid;
UPDATE work_order_spare_part_requirements r
SET warehouse_id = c.warehouse_id
FROM repair_campaign_material_requirements c
WHERE r.campaign_requirement_id = c.id
  AND r.source_type = 'REPAIR_CAMPAIGN_WORK_ITEM';

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM work_order_spare_part_requirements r
        LEFT JOIN repair_campaign_material_requirements c
          ON c.id = r.campaign_requirement_id AND c.is_deleted = false
        WHERE r.is_deleted = false AND r.source_type = 'REPAIR_CAMPAIGN_WORK_ITEM'
          AND (c.id IS NULL OR r.warehouse_id IS NULL OR r.spare_part_id <> c.spare_part_id
               OR r.warehouse_id <> c.warehouse_id)
    ) OR EXISTS (
        SELECT 1
        FROM reservations v
        LEFT JOIN work_order_spare_part_requirements r
          ON r.id = v.requirement_id AND r.work_order_id = v.work_order_id
         AND r.spare_part_id = v.spare_part_id AND r.is_deleted = false
        WHERE v.requirement_id IS NOT NULL AND v.is_deleted = false
          AND (r.id IS NULL OR (r.warehouse_id IS NOT NULL AND r.warehouse_id <> v.warehouse_id))
    ) THEN
        RAISE EXCEPTION 'RC_V5_1_RESERVATION_OWNERSHIP_REMEDIATION_REQUIRED';
    END IF;
END $$;

ALTER TABLE work_order_spare_part_requirements
    ADD CONSTRAINT fk_wo_spare_req_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    ADD CONSTRAINT uq_wo_spare_req_owner UNIQUE (id, work_order_id, spare_part_id);
ALTER TABLE reservations
    ADD CONSTRAINT fk_reservation_requirement_owner
    FOREIGN KEY (requirement_id, work_order_id, spare_part_id)
    REFERENCES work_order_spare_part_requirements(id, work_order_id, spare_part_id);

ALTER TABLE work_order_spare_part_requirements
    ADD CONSTRAINT chk_wo_spare_req_campaign_source_coupling CHECK (
        (source_type='REPAIR_CAMPAIGN_WORK_ITEM' AND campaign_requirement_id IS NOT NULL)
        OR (source_type<>'REPAIR_CAMPAIGN_WORK_ITEM' AND campaign_requirement_id IS NULL));

CREATE OR REPLACE FUNCTION guard_campaign_material_requirement_removal() RETURNS trigger AS $$
BEGIN
    IF OLD.is_deleted=false AND NEW.is_deleted=true AND EXISTS (
        SELECT 1 FROM work_order_spare_part_requirements r
        WHERE r.campaign_requirement_id=OLD.id AND r.is_deleted=false) THEN
        RAISE EXCEPTION 'RC_MATERIAL_REQUIREMENT_IN_USE';
    END IF;
    RETURN NEW;
END $$ LANGUAGE plpgsql;
CREATE TRIGGER trg_guard_campaign_material_requirement_removal
    BEFORE UPDATE OF is_deleted ON repair_campaign_material_requirements
    FOR EACH ROW EXECUTE FUNCTION guard_campaign_material_requirement_removal();

CREATE OR REPLACE FUNCTION require_new_work_order_reservation_identity() RETURNS trigger AS $$
BEGIN
    IF NEW.work_order_id IS NOT NULL AND NEW.requirement_id IS NULL THEN
        RAISE EXCEPTION 'RESERVATION_REQUIREMENT_REQUIRED';
    END IF;
    RETURN NEW;
END $$ LANGUAGE plpgsql;
CREATE TRIGGER trg_require_new_work_order_reservation_identity
    BEFORE INSERT ON reservations FOR EACH ROW EXECUTE FUNCTION require_new_work_order_reservation_identity();

CREATE OR REPLACE FUNCTION validate_reservation_requirement_owner() RETURNS trigger AS $$
DECLARE owner record;
BEGIN
    IF NEW.requirement_id IS NULL THEN RETURN NEW; END IF;
    SELECT r.is_deleted, r.warehouse_id INTO owner
    FROM work_order_spare_part_requirements r
    WHERE r.id=NEW.requirement_id AND r.work_order_id=NEW.work_order_id AND r.spare_part_id=NEW.spare_part_id;
    IF NOT FOUND OR owner.is_deleted THEN
        RAISE EXCEPTION 'RESERVATION_REQUIREMENT_INVALID';
    END IF;
    IF owner.warehouse_id IS NOT NULL AND owner.warehouse_id <> NEW.warehouse_id THEN
        RAISE EXCEPTION 'RESERVATION_WAREHOUSE_MISMATCH';
    END IF;
    RETURN NEW;
END $$ LANGUAGE plpgsql;
CREATE TRIGGER trg_validate_reservation_requirement_owner
    BEFORE INSERT OR UPDATE OF requirement_id,work_order_id,spare_part_id,warehouse_id,is_deleted,status ON reservations
    FOR EACH ROW EXECUTE FUNCTION validate_reservation_requirement_owner();

CREATE OR REPLACE FUNCTION guard_active_reservation_requirement() RETURNS trigger AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM reservations v WHERE v.requirement_id=OLD.id
               AND v.status='ACTIVE' AND v.is_deleted=false)
       AND (TG_OP='DELETE' OR NEW.is_deleted OR NEW.work_order_id<>OLD.work_order_id
            OR NEW.spare_part_id<>OLD.spare_part_id OR NEW.required_qty<>OLD.required_qty
            OR NEW.warehouse_id IS DISTINCT FROM OLD.warehouse_id) THEN
        RAISE EXCEPTION 'RESERVATION_REQUIREMENT_IN_USE';
    END IF;
    RETURN CASE WHEN TG_OP='DELETE' THEN OLD ELSE NEW END;
END $$ LANGUAGE plpgsql;
CREATE TRIGGER trg_guard_active_reservation_requirement
    BEFORE UPDATE OR DELETE ON work_order_spare_part_requirements
    FOR EACH ROW EXECUTE FUNCTION guard_active_reservation_requirement();
