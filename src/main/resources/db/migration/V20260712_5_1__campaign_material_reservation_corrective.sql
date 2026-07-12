DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM stock_movements WHERE quantity::text IN ('NaN','Infinity','-Infinity') OR abs(quantity) >= 1000000000000000)
       OR EXISTS (SELECT 1 FROM repair_material_usages WHERE quantity::text IN ('NaN','Infinity','-Infinity') OR abs(quantity) >= 1000000000000000)
       OR EXISTS (SELECT 1 FROM repair_material_returns WHERE quantity::text IN ('NaN','Infinity','-Infinity') OR abs(quantity) >= 1000000000000000)
       OR EXISTS (SELECT 1 FROM maintenance_template_spare_part_requirements WHERE quantity::text IN ('NaN','Infinity','-Infinity') OR abs(quantity) >= 1000000000000000)
       OR EXISTS (SELECT 1 FROM maintenance_regulation_spare_part_requirements WHERE quantity::text IN ('NaN','Infinity','-Infinity') OR abs(quantity) >= 1000000000000000) THEN
        RAISE EXCEPTION 'RC_V5_1_NUMERIC_OVERFLOW_REMEDIATION_REQUIRED';
    END IF;
END $$;

DROP VIEW stock_movements;
ALTER TABLE warehouse_stock_ledger_metadata ALTER COLUMN submitted_quantity TYPE numeric(19,4) USING round(submitted_quantity::numeric,4);
ALTER TABLE repair_material_usages ALTER COLUMN quantity TYPE numeric(19,4) USING round(quantity::numeric,4);
ALTER TABLE repair_material_returns ALTER COLUMN quantity TYPE numeric(19,4) USING round(quantity::numeric,4);
ALTER TABLE maintenance_template_spare_part_requirements ALTER COLUMN quantity TYPE numeric(19,4) USING round(quantity::numeric,4);
ALTER TABLE maintenance_regulation_spare_part_requirements ALTER COLUMN quantity TYPE numeric(19,4) USING round(quantity::numeric,4);

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
