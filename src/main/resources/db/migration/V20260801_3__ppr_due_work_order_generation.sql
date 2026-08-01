ALTER TABLE maintenance_regulations
    ADD COLUMN IF NOT EXISTS lead_time_days integer;

UPDATE maintenance_regulations
SET lead_time_days = 7
WHERE lead_time_days IS NULL OR lead_time_days < 0 OR lead_time_days > 365;

ALTER TABLE maintenance_regulations
    ALTER COLUMN lead_time_days SET DEFAULT 7,
    ALTER COLUMN lead_time_days SET NOT NULL;

ALTER TABLE maintenance_regulations
    DROP CONSTRAINT IF EXISTS ck_maintenance_regulations_lead_time_days;

ALTER TABLE maintenance_regulations
    ADD CONSTRAINT ck_maintenance_regulations_lead_time_days
        CHECK (lead_time_days BETWEEN 0 AND 365);

UPDATE equipment_maintenance_rules
SET lead_time_days = NULL
WHERE lead_time_days < 0 OR lead_time_days > 365;

ALTER TABLE equipment_maintenance_rules
    DROP CONSTRAINT IF EXISTS ck_equipment_maintenance_rules_lead_time_days;

ALTER TABLE equipment_maintenance_rules
    ADD CONSTRAINT ck_equipment_maintenance_rules_lead_time_days
        CHECK (lead_time_days IS NULL OR lead_time_days BETWEEN 0 AND 365);

CREATE UNIQUE INDEX IF NOT EXISTS uq_work_orders_active_ppr_task
    ON work_orders (ppr_task_id)
    WHERE ppr_task_id IS NOT NULL
      AND is_deleted = false
      AND status NOT IN ('COMPLETED', 'CLOSED', 'CANCELLED');
