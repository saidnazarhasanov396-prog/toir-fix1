CREATE TABLE IF NOT EXISTS equipment_commissioning_acts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,
    equipment_id uuid NOT NULL REFERENCES equipment(id),
    source_warehouse_id uuid NOT NULL REFERENCES warehouses(id),
    warehouse_item_id uuid NOT NULL REFERENCES warehouse_equipment_items(id),
    target_department_id uuid NOT NULL REFERENCES departments(id),
    target_location_id uuid REFERENCES locations(id),
    responsible_employee_id uuid NOT NULL REFERENCES hr_employees(id),
    act_number varchar(255) NOT NULL,
    act_date date NOT NULL,
    commissioned_at date NOT NULL,
    operation_start_date date NOT NULL,
    committee_json text,
    notes text,
    status varchar(32) NOT NULL DEFAULT 'DRAFT',
    approval_request_id uuid REFERENCES approval_requests(id),
    submitted_at timestamptz,
    approved_at timestamptz,
    approved_by uuid,
    rejected_at timestamptz,
    rejection_reason text,
    warehouse_movement_id uuid REFERENCES warehouse_stock_ledger_metadata(id),
    CONSTRAINT equipment_commissioning_status_check
        CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT equipment_commissioning_dates_check
        CHECK (operation_start_date >= commissioned_at)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_equipment_commissioning_act_number
    ON equipment_commissioning_acts (act_number)
    WHERE is_deleted = false;

CREATE UNIQUE INDEX IF NOT EXISTS uq_equipment_commissioning_approved_equipment
    ON equipment_commissioning_acts (equipment_id)
    WHERE is_deleted = false AND status = 'APPROVED';

CREATE INDEX IF NOT EXISTS idx_equipment_commissioning_equipment
    ON equipment_commissioning_acts (equipment_id, status);

CREATE INDEX IF NOT EXISTS idx_equipment_commissioning_department
    ON equipment_commissioning_acts (target_department_id, status);

INSERT INTO approval_templates (
    code, name, target_type, route_policy, approver_role,
    sla_hours, escalation_hours, escalation_severity, active, action_type
)
SELECT
    'EQUIPMENT_COMMISSIONING_APPROVE',
    'Equipment Commissioning Approval',
    'EQUIPMENT_COMMISSIONING',
    'ROLE_BASED',
    'DEPARTMENT_HEAD',
    24,
    8,
    'WARNING',
    true,
    'APPROVE'
WHERE NOT EXISTS (
    SELECT 1 FROM approval_templates
    WHERE target_type = 'EQUIPMENT_COMMISSIONING'
      AND action_type = 'APPROVE'
      AND is_deleted = false
);

INSERT INTO approval_template_steps (template_id, step_order, approver_role)
SELECT template.id, 1, 'DEPARTMENT_HEAD'
FROM approval_templates template
WHERE template.target_type = 'EQUIPMENT_COMMISSIONING'
  AND template.action_type = 'APPROVE'
  AND template.is_deleted = false
  AND NOT EXISTS (
      SELECT 1 FROM approval_template_steps step
      WHERE step.template_id = template.id AND step.step_order = 1
  );

UPDATE roles
SET permissions = (
    SELECT jsonb_agg(DISTINCT permission ORDER BY permission)
    FROM jsonb_array_elements_text(
        COALESCE(permissions, '[]'::jsonb)
        || '[
            "EQUIPMENT_COMMISSIONING_READ",
            "EQUIPMENT_COMMISSIONING_CREATE",
            "EQUIPMENT_COMMISSIONING_UPDATE",
            "EQUIPMENT_COMMISSIONING_SUBMIT",
            "EQUIPMENT_COMMISSIONING_CANCEL"
        ]'::jsonb
    ) AS permission
)
WHERE code IN ('SYSTEM_ADMIN', 'MAINTENANCE_MANAGER', 'WAREHOUSE_MANAGER')
  AND is_deleted = false;
