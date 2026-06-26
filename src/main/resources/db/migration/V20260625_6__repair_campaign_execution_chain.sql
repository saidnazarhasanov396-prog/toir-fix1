ALTER TABLE repair_campaigns
    ADD COLUMN IF NOT EXISTS scope_type varchar(64),
    ADD COLUMN IF NOT EXISTS equipment_type_id uuid;

UPDATE repair_campaigns
SET scope_type = COALESCE(scope_type, 'CUSTOM')
WHERE scope_type IS NULL;

ALTER TABLE repair_campaigns
    ALTER COLUMN scope_type SET DEFAULT 'CUSTOM',
    ALTER COLUMN scope_type SET NOT NULL;

ALTER TABLE repair_campaigns
    DROP CONSTRAINT IF EXISTS repair_campaigns_scope_type_check;

ALTER TABLE repair_campaigns
    ADD CONSTRAINT repair_campaigns_scope_type_check
        CHECK (scope_type IN ('EQUIPMENT', 'EQUIPMENT_TYPE', 'DEPARTMENT', 'CROSS_DEPARTMENT', 'PLANT_SHUTDOWN', 'CUSTOM'));

CREATE TABLE IF NOT EXISTS repair_campaign_departments (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,
    campaign_id uuid NOT NULL,
    department_id uuid NOT NULL,
    role varchar(64) NOT NULL,
    planned_budget double precision NOT NULL DEFAULT 0,
    notes text,
    CONSTRAINT repair_campaign_departments_role_check
        CHECK (role IN ('OWNER', 'EXECUTOR', 'PARTICIPANT', 'APPROVER'))
);

ALTER TABLE repair_campaign_departments
    DROP CONSTRAINT IF EXISTS fk_repair_campaign_departments_campaign;

ALTER TABLE repair_campaign_departments
    ADD CONSTRAINT fk_repair_campaign_departments_campaign
        FOREIGN KEY (campaign_id) REFERENCES repair_campaigns(id);

CREATE INDEX IF NOT EXISTS idx_repair_campaign_departments_campaign_id
    ON repair_campaign_departments (campaign_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_repair_campaign_departments_department_id
    ON repair_campaign_departments (department_id)
    WHERE is_deleted = false;

CREATE UNIQUE INDEX IF NOT EXISTS uq_repair_campaign_departments_active_role
    ON repair_campaign_departments (campaign_id, department_id, role)
    WHERE is_deleted = false;

ALTER TABLE work_orders
    ADD COLUMN IF NOT EXISTS repair_campaign_id uuid,
    ADD COLUMN IF NOT EXISTS repair_campaign_stage_id uuid;

CREATE INDEX IF NOT EXISTS idx_work_orders_repair_campaign_id
    ON work_orders (repair_campaign_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_work_orders_repair_campaign_stage_id
    ON work_orders (repair_campaign_stage_id)
    WHERE is_deleted = false;

ALTER TABLE work_orders
    DROP CONSTRAINT IF EXISTS fk_work_orders_repair_campaign;

ALTER TABLE work_orders
    ADD CONSTRAINT fk_work_orders_repair_campaign
        FOREIGN KEY (repair_campaign_id) REFERENCES repair_campaigns(id);

ALTER TABLE work_orders
    DROP CONSTRAINT IF EXISTS fk_work_orders_repair_campaign_stage;

ALTER TABLE work_orders
    ADD CONSTRAINT fk_work_orders_repair_campaign_stage
        FOREIGN KEY (repair_campaign_stage_id) REFERENCES repair_campaign_stages(id);
