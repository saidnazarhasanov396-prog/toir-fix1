CREATE TABLE IF NOT EXISTS counteragents (
    id uuid PRIMARY KEY,
    created_at timestamp,
    updated_at timestamp,
    is_deleted boolean NOT NULL DEFAULT false,
    code varchar(100) NOT NULL UNIQUE,
    name varchar(255) NOT NULL,
    tax_number varchar(255),
    base_inn varchar(255),
    contact_person varchar(255),
    phone varchar(255),
    email varchar(255),
    address text,
    specialization varchar(255),
    director_name varchar(255),
    bank_name varchar(255),
    bank_account varchar(255),
    mfo varchar(255),
    status varchar(32) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT ck_counteragents_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'BLOCKED'))
);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'audit_logs_module_check'
          AND conrelid = 'audit_logs'::regclass
    ) THEN
        ALTER TABLE audit_logs DROP CONSTRAINT audit_logs_module_check;
    END IF;
    ALTER TABLE audit_logs
        ADD CONSTRAINT audit_logs_module_check
        CHECK (module IN (
            'EQUIPMENT', 'EQUIPMENT_TYPE', 'WAREHOUSE', 'LOCATION', 'DEPARTMENT',
            'COUNTERAGENT', 'COUNTERAGENT_CONTRACT', 'COUNTERAGENT_WORK',
            'CONTRACTOR', 'CONTRACTOR_CONTRACT', 'CONTRACTOR_WORK',
            'MANUFACTURER', 'MATERIAL', 'UNIT_OF_MEASUREMENT', 'COST_CATEGORY',
            'CRITICALITY_CLASS', 'DEFECT_CATEGORY', 'DEFECT_SEVERITY', 'FAILURE_REASON',
            'ROOT_CAUSE', 'SERVICE_CLASS', 'ROLE', 'USER', 'SPARE_PART', 'VEHICLE',
            'PPR_PLAN', 'WORK_ORDER', 'REPAIR_REQUEST', 'DEFECT', 'MAINTENANCE_TEMPLATE',
            'MAINTENANCE_REGULATION', 'BRIGADE', 'CALIBRATION_RECORD', 'OEE_RECORD',
            'ACTUAL_COST', 'APPROVAL_REQUEST', 'BRIGADE_MEMBER', 'COMPLETION_ACT',
            'CONDITION_READING', 'DEFECT_LIST', 'DEFECT_LIST_LINE', 'EQUIPMENT_KPI',
            'EQUIPMENT_NODE', 'EQUIPMENT_SPARE_PART', 'FILE_ASSET', 'LABOR_ENTRY',
            'MAINTENANCE_BUDGET', 'MAINTENANCE_KPI', 'PLANNED_SHUTDOWN',
            'PROCUREMENT_REQUEST', 'REPAIR_CAMPAIGN', 'REPAIR_CAMPAIGN_STAGE',
            'SAFETY_PERMIT', 'STOCK_MOVEMENT', 'TECHNICAL_DOCUMENT', 'WEBHOOK',
            'RESERVATION', 'WORK_EXECUTION', 'EQUIPMENT_METER', 'METER_READING',
            'DOWNTIME_EVENT', 'INSPECTION_ROUTE', 'INSPECTION_CHECKPOINT',
            'INSPECTION_ROUND', 'INSPECTION_ROUND_RESULT', 'CERTIFICATION_TYPE',
            'USER_CERTIFICATION', 'RELIABILITY_METRIC', 'EMPLOYEE', 'TIMESHEET_ENTRY',
            'PPR_TASK', 'REPAIR_MATERIAL_USAGE', 'INTEGRATION_SYNC_LOG', 'SLA_RULE',
            'FINANCIAL_APPROVAL_RULE', 'INTEGRATION_ENDPOINT', 'USERS', 'AUTH',
            'CONTRACTORS', 'PROJECTS', 'MAINTENANCE', 'DEPARTMENTS', 'OTHER',
            'WAREHOUSE_BIN', 'WAREHOUSE_TASK', 'INVENTORY_COUNT_SESSION',
            'INVENTORY_TRANSACTION', 'WAREHOUSE_WRITEOFF', 'REPAIR_MATERIAL_RETURN'
        ));
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'actual_costs_source_type_check'
          AND conrelid = 'actual_costs'::regclass
    ) THEN
        ALTER TABLE actual_costs DROP CONSTRAINT actual_costs_source_type_check;
    END IF;
    ALTER TABLE actual_costs
        ADD CONSTRAINT actual_costs_source_type_check
        CHECK (source_type IN (
            'WORK_ORDER',
            'REPAIR_REQUEST',
            'COUNTERAGENT_WORK',
            'CONTRACTOR_WORK',
            'MATERIAL_ISSUE',
            'LABOR_ENTRY',
            'PROCUREMENT_RECEIPT',
            'WORK_ORDER_MANUAL_WITH_REASON'
        ));
END $$;

CREATE INDEX IF NOT EXISTS idx_counteragents_code
    ON counteragents(code)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_counteragents_name
    ON counteragents(name)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_counteragents_tax_number
    ON counteragents(tax_number)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_counteragents_base_inn
    ON counteragents(base_inn)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_counteragents_status
    ON counteragents(status)
    WHERE is_deleted = false;

CREATE TABLE IF NOT EXISTS supplier_counteragent_map (
    supplier_id uuid PRIMARY KEY,
    counteragent_id uuid NOT NULL REFERENCES counteragents(id)
);

CREATE TABLE IF NOT EXISTS contractor_counteragent_map (
    contractor_id uuid PRIMARY KEY,
    counteragent_id uuid NOT NULL REFERENCES counteragents(id)
);

INSERT INTO counteragents (
    id,
    created_at,
    updated_at,
    is_deleted,
    code,
    name,
    tax_number,
    contact_person,
    phone,
    email,
    specialization,
    director_name,
    bank_name,
    bank_account,
    mfo,
    status
)
SELECT
    c.id,
    c.created_at,
    c.updated_at,
    c.is_deleted,
    c.code,
    c.name,
    c.tax_number,
    c.contact_person,
    c.phone,
    c.email,
    c.specialization,
    c.director_name,
    c.bank_name,
    c.bank_account,
    c.mfo,
    c.status
FROM contractors c
WHERE NOT EXISTS (
    SELECT 1
    FROM counteragents ca
    WHERE ca.id = c.id
);

INSERT INTO contractor_counteragent_map (contractor_id, counteragent_id)
SELECT c.id, c.id
FROM contractors c
ON CONFLICT (contractor_id) DO NOTHING;

INSERT INTO counteragents (
    id,
    created_at,
    updated_at,
    is_deleted,
    code,
    name,
    tax_number,
    base_inn,
    contact_person,
    phone,
    email,
    address,
    director_name,
    bank_name,
    bank_account,
    mfo,
    status
)
SELECT
    s.id,
    s.created_at,
    s.updated_at,
    s.is_deleted,
    CASE
        WHEN EXISTS (SELECT 1 FROM counteragents ca WHERE ca.code = s.code)
            THEN CONCAT('CA-', LEFT(REPLACE(s.id::text, '-', ''), 12))
        ELSE s.code
    END,
    s.name,
    s.tax_number,
    s.base_inn,
    s.contact_person,
    s.phone,
    s.email,
    s.address,
    s.director_name,
    s.bank_name,
    s.bank_account,
    s.mfo,
    CASE WHEN s.active IS FALSE THEN 'INACTIVE' ELSE 'ACTIVE' END
FROM suppliers s
WHERE NOT EXISTS (
    SELECT 1
    FROM counteragents ca
    WHERE ca.id = s.id
)
AND NOT EXISTS (
    SELECT 1
    FROM counteragents ca
    WHERE s.tax_number IS NOT NULL
      AND s.tax_number <> ''
      AND ca.tax_number = s.tax_number
);

INSERT INTO supplier_counteragent_map (supplier_id, counteragent_id)
SELECT s.id, ca.id
FROM suppliers s
JOIN counteragents ca
  ON s.tax_number IS NOT NULL
 AND s.tax_number <> ''
 AND ca.tax_number = s.tax_number
ON CONFLICT (supplier_id) DO NOTHING;

INSERT INTO supplier_counteragent_map (supplier_id, counteragent_id)
SELECT s.id, ca.id
FROM suppliers s
JOIN counteragents ca
  ON s.base_inn IS NOT NULL
 AND s.base_inn <> ''
 AND ca.base_inn = s.base_inn
WHERE NOT EXISTS (
    SELECT 1
    FROM supplier_counteragent_map m
    WHERE m.supplier_id = s.id
)
ON CONFLICT (supplier_id) DO NOTHING;

INSERT INTO supplier_counteragent_map (supplier_id, counteragent_id)
SELECT s.id, s.id
FROM suppliers s
WHERE EXISTS (
    SELECT 1
    FROM counteragents ca
    WHERE ca.id = s.id
)
ON CONFLICT (supplier_id) DO NOTHING;

ALTER TABLE purchase_orders
    ADD COLUMN IF NOT EXISTS counteragent_id uuid;

ALTER TABLE procurement_requests
    ADD COLUMN IF NOT EXISTS counteragent_id uuid;

ALTER TABLE equipment
    ADD COLUMN IF NOT EXISTS counteragent_id uuid,
    ADD COLUMN IF NOT EXISTS warranty_counteragent_id uuid;

ALTER TABLE repair_requests
    ADD COLUMN IF NOT EXISTS warranty_counteragent_id uuid;

ALTER TABLE procurement_request_lines
    ADD COLUMN IF NOT EXISTS warranty_counteragent_id uuid;

ALTER TABLE spare_parts
    ADD COLUMN IF NOT EXISTS preferred_counteragent_id uuid;

ALTER TABLE contractor_contracts
    ADD COLUMN IF NOT EXISTS counteragent_id uuid;

ALTER TABLE contractor_works
    ADD COLUMN IF NOT EXISTS counteragent_id uuid;

ALTER TABLE work_orders
    ADD COLUMN IF NOT EXISTS counteragent_id uuid;

ALTER TABLE purchase_orders
    ALTER COLUMN supplier_id DROP NOT NULL;

ALTER TABLE contractor_contracts
    ALTER COLUMN contractor_id DROP NOT NULL;

ALTER TABLE contractor_works
    ALTER COLUMN contractor_id DROP NOT NULL;

UPDATE purchase_orders po
SET counteragent_id = m.counteragent_id
FROM supplier_counteragent_map m
WHERE po.supplier_id = m.supplier_id
  AND po.counteragent_id IS NULL;

UPDATE procurement_requests pr
SET counteragent_id = m.counteragent_id
FROM supplier_counteragent_map m
WHERE pr.supplier_id = m.supplier_id
  AND pr.counteragent_id IS NULL;

UPDATE equipment e
SET counteragent_id = m.counteragent_id
FROM supplier_counteragent_map m
WHERE e.supplier_id = m.supplier_id
  AND e.counteragent_id IS NULL;

UPDATE equipment e
SET warranty_counteragent_id = m.counteragent_id
FROM supplier_counteragent_map m
WHERE e.warranty_supplier_id = m.supplier_id
  AND e.warranty_counteragent_id IS NULL;

UPDATE repair_requests rr
SET warranty_counteragent_id = m.counteragent_id
FROM supplier_counteragent_map m
WHERE rr.warranty_supplier_id = m.supplier_id
  AND rr.warranty_counteragent_id IS NULL;

UPDATE procurement_request_lines prl
SET warranty_counteragent_id = m.counteragent_id
FROM supplier_counteragent_map m
WHERE prl.warranty_supplier_id = m.supplier_id
  AND prl.warranty_counteragent_id IS NULL;

UPDATE spare_parts sp
SET preferred_counteragent_id = m.counteragent_id
FROM supplier_counteragent_map m
WHERE sp.preferred_supplier_id = m.supplier_id
  AND sp.preferred_counteragent_id IS NULL;

UPDATE contractor_contracts cc
SET counteragent_id = m.counteragent_id
FROM contractor_counteragent_map m
WHERE cc.contractor_id = m.contractor_id
  AND cc.counteragent_id IS NULL;

UPDATE contractor_works cw
SET counteragent_id = m.counteragent_id
FROM contractor_counteragent_map m
WHERE cw.contractor_id = m.contractor_id
  AND cw.counteragent_id IS NULL;

UPDATE work_orders wo
SET counteragent_id = m.counteragent_id
FROM contractor_counteragent_map m
WHERE wo.contractor_id = m.contractor_id
  AND wo.counteragent_id IS NULL;

ALTER TABLE purchase_orders
    ADD CONSTRAINT fk_purchase_orders_counteragent
    FOREIGN KEY (counteragent_id) REFERENCES counteragents(id);

ALTER TABLE procurement_requests
    ADD CONSTRAINT fk_procurement_requests_counteragent
    FOREIGN KEY (counteragent_id) REFERENCES counteragents(id);

ALTER TABLE equipment
    ADD CONSTRAINT fk_equipment_counteragent
    FOREIGN KEY (counteragent_id) REFERENCES counteragents(id);

ALTER TABLE equipment
    ADD CONSTRAINT fk_equipment_warranty_counteragent
    FOREIGN KEY (warranty_counteragent_id) REFERENCES counteragents(id);

ALTER TABLE repair_requests
    ADD CONSTRAINT fk_repair_requests_warranty_counteragent
    FOREIGN KEY (warranty_counteragent_id) REFERENCES counteragents(id);

ALTER TABLE procurement_request_lines
    ADD CONSTRAINT fk_procurement_request_lines_warranty_counteragent
    FOREIGN KEY (warranty_counteragent_id) REFERENCES counteragents(id);

ALTER TABLE spare_parts
    ADD CONSTRAINT fk_spare_parts_preferred_counteragent
    FOREIGN KEY (preferred_counteragent_id) REFERENCES counteragents(id);

ALTER TABLE contractor_contracts
    ADD CONSTRAINT fk_contractor_contracts_counteragent
    FOREIGN KEY (counteragent_id) REFERENCES counteragents(id);

ALTER TABLE contractor_works
    ADD CONSTRAINT fk_contractor_works_counteragent
    FOREIGN KEY (counteragent_id) REFERENCES counteragents(id);

ALTER TABLE work_orders
    ADD CONSTRAINT fk_work_orders_counteragent
    FOREIGN KEY (counteragent_id) REFERENCES counteragents(id);

CREATE OR REPLACE FUNCTION append_counteragent_role_permissions(role_code text, permissions_to_add text[])
RETURNS void AS $$
BEGIN
    UPDATE roles r
    SET permissions = (
        SELECT jsonb_agg(DISTINCT permission ORDER BY permission)
        FROM (
            SELECT jsonb_array_elements_text(
                CASE
                    WHEN jsonb_typeof(COALESCE(r.permissions, '[]'::jsonb)) = 'array'
                        THEN COALESCE(r.permissions, '[]'::jsonb)
                    ELSE '[]'::jsonb
                END
            ) AS permission
            UNION
            SELECT unnest(permissions_to_add) AS permission
        ) merged_permissions
    )
    WHERE r.code = role_code
      AND r.is_deleted = false;
END;
$$ LANGUAGE plpgsql;

SELECT append_counteragent_role_permissions('SYSTEM_ADMIN', ARRAY['*']);
SELECT append_counteragent_role_permissions('SUPPLY_SPECIALIST', ARRAY[
    'COUNTERAGENT_READ',
    'COUNTERAGENT_CREATE',
    'COUNTERAGENT_UPDATE'
]);
SELECT append_counteragent_role_permissions('FINANCE_MANAGER', ARRAY[
    'COUNTERAGENT_READ',
    'COUNTERAGENT_CREATE',
    'COUNTERAGENT_UPDATE'
]);
SELECT append_counteragent_role_permissions('ECONOMIST', ARRAY[
    'COUNTERAGENT_READ'
]);
SELECT append_counteragent_role_permissions('VIEWER', ARRAY[
    'COUNTERAGENT_READ'
]);

DROP FUNCTION append_counteragent_role_permissions(text, text[]);
