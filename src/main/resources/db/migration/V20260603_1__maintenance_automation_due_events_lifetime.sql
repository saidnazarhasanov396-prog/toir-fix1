ALTER TABLE maintenance_regulations
    ADD COLUMN IF NOT EXISTS automation_action varchar(64) NOT NULL DEFAULT 'REQUIRE_APPROVAL',
    ADD COLUMN IF NOT EXISTS duplicate_policy varchar(64) NOT NULL DEFAULT 'ONE_ITEM_PER_CYCLE',
    ADD COLUMN IF NOT EXISTS lead_time_days integer,
    ADD COLUMN IF NOT EXISTS lead_meter_percent double precision,
    ADD COLUMN IF NOT EXISTS default_department_id uuid,
    ADD COLUMN IF NOT EXISTS default_responsible_id uuid,
    ADD COLUMN IF NOT EXISTS default_priority varchar(64),
    ADD COLUMN IF NOT EXISTS requires_approval boolean NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS approval_role varchar(128),
    ADD COLUMN IF NOT EXISTS approval_permission varchar(128);

ALTER TABLE equipment
    ADD COLUMN IF NOT EXISTS operation_start_date date,
    ADD COLUMN IF NOT EXISTS expected_lifetime_months integer,
    ADD COLUMN IF NOT EXISTS expected_lifetime_years integer;

CREATE TABLE IF NOT EXISTS maintenance_due_events (
    id uuid PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    equipment_id uuid NOT NULL,
    regulation_id uuid,
    equipment_maintenance_rule_id uuid,
    template_id uuid,
    status varchar(64) NOT NULL,
    due_status varchar(64) NOT NULL,
    trigger_source varchar(64) NOT NULL,
    cycle_key varchar(512) NOT NULL,
    due_at timestamp with time zone,
    meter_type varchar(64),
    meter_current_value double precision,
    meter_anchor_value double precision,
    meter_interval double precision,
    meter_remaining double precision,
    created_task_id uuid,
    created_work_order_id uuid,
    detected_at timestamp with time zone NOT NULL,
    resolved_at timestamp with time zone,
    resolution_reason text,
    explanation text
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_maintenance_due_events_cycle_active
    ON maintenance_due_events (cycle_key)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_maintenance_due_events_equipment
    ON maintenance_due_events (equipment_id);

CREATE INDEX IF NOT EXISTS idx_maintenance_due_events_regulation
    ON maintenance_due_events (regulation_id);

CREATE INDEX IF NOT EXISTS idx_maintenance_due_events_status
    ON maintenance_due_events (status);

CREATE INDEX IF NOT EXISTS idx_maintenance_due_events_due_status
    ON maintenance_due_events (due_status);

CREATE INDEX IF NOT EXISTS idx_maintenance_due_events_created_task
    ON maintenance_due_events (created_task_id);

CREATE INDEX IF NOT EXISTS idx_maintenance_due_events_created_work_order
    ON maintenance_due_events (created_work_order_id);

ALTER TABLE ppr_tasks
    ADD COLUMN IF NOT EXISTS maintenance_due_event_id uuid,
    ADD COLUMN IF NOT EXISTS cycle_key varchar(512);

ALTER TABLE work_orders
    ADD COLUMN IF NOT EXISTS maintenance_due_event_id uuid,
    ADD COLUMN IF NOT EXISTS cycle_key varchar(512);

CREATE INDEX IF NOT EXISTS idx_ppr_tasks_maintenance_due_event
    ON ppr_tasks (maintenance_due_event_id);

CREATE INDEX IF NOT EXISTS idx_ppr_tasks_cycle_key
    ON ppr_tasks (cycle_key);

CREATE INDEX IF NOT EXISTS idx_work_orders_maintenance_due_event
    ON work_orders (maintenance_due_event_id);

CREATE INDEX IF NOT EXISTS idx_work_orders_cycle_key
    ON work_orders (cycle_key);

CREATE TABLE IF NOT EXISTS operational_issues (
    id uuid PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    type varchar(64) NOT NULL,
    status varchar(64) NOT NULL,
    severity varchar(64) NOT NULL,
    equipment_id uuid,
    department_id uuid,
    source_type varchar(128) NOT NULL,
    source_id uuid NOT NULL,
    title varchar(255) NOT NULL,
    message text,
    detected_at timestamp with time zone NOT NULL,
    resolved_at timestamp with time zone
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_operational_issues_open_source
    ON operational_issues (source_type, source_id, status)
    WHERE is_deleted = false AND status = 'OPEN';

CREATE INDEX IF NOT EXISTS idx_operational_issues_department
    ON operational_issues (department_id);

CREATE INDEX IF NOT EXISTS idx_operational_issues_status
    ON operational_issues (status);

CREATE OR REPLACE FUNCTION append_role_permissions(role_code text, permissions_to_add text[])
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE roles r
    SET permissions = (
            SELECT COALESCE(jsonb_agg(DISTINCT permission), '[]'::jsonb)
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
END;
$$;

INSERT INTO roles (id, created_at, updated_at, is_deleted, is_system, code, name, description, permissions)
SELECT gen_random_uuid(), now(), now(), false, false, 'DEPARTMENT_HEAD', 'Department head',
       'Department-scoped maintenance lead', '[]'::jsonb
WHERE NOT EXISTS (
    SELECT 1 FROM roles WHERE code = 'DEPARTMENT_HEAD' AND is_deleted = false
);

SELECT append_role_permissions('SYSTEM_ADMIN', ARRAY['*']);
SELECT append_role_permissions('DEPARTMENT_HEAD', ARRAY[
    'read',
    'EQUIPMENT_READ',
    'METER_READ',
    'METER_READING_CREATE',
    'MAINTENANCE_REGULATION_READ',
    'MAINTENANCE_EVENT_READ',
    'MAINTENANCE_EVENT_APPROVE',
    'MAINTENANCE_EVENT_CANCEL',
    'MAINTENANCE_AUTOMATION_RUN',
    'PPR_PLAN_READ',
    'PPR_TASK_READ',
    'PPR_TASK_APPROVE',
    'WORK_ORDER_READ',
    'WORK_ORDER_CREATE',
    'WORK_ORDER_APPROVE',
    'WORK_ORDER_START',
    'WORK_ORDER_COMPLETE',
    'NOTIFICATION_READ',
    'NOTIFICATION_MARK_READ'
]);

DROP FUNCTION append_role_permissions(text, text[]);
