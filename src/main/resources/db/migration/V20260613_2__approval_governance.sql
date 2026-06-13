ALTER TABLE approval_requests
    ADD COLUMN IF NOT EXISTS expires_at timestamptz,
    ADD COLUMN IF NOT EXISTS escalated_at timestamptz,
    ADD COLUMN IF NOT EXISTS executed boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS executed_at timestamptz,
    ADD COLUMN IF NOT EXISTS execution_id uuid;

ALTER TABLE approval_steps
    ADD COLUMN IF NOT EXISTS version bigint,
    ADD COLUMN IF NOT EXISTS decided_by_id uuid,
    ADD COLUMN IF NOT EXISTS delegated_for_id uuid;

UPDATE approval_steps
SET version = 0
WHERE version IS NULL;

CREATE TABLE IF NOT EXISTS approval_history (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    approval_id uuid NOT NULL REFERENCES approval_requests(id),
    old_status varchar(50),
    new_status varchar(50) NOT NULL,
    changed_by uuid,
    comment text,
    changed_at timestamptz NOT NULL DEFAULT now(),
    action_type varchar(50),
    target_type varchar(100),
    target_id uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false
);

CREATE INDEX IF NOT EXISTS idx_approval_history_approval
    ON approval_history(approval_id, changed_at);

CREATE TABLE IF NOT EXISTS approval_delegates (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    approver_id uuid NOT NULL,
    delegate_id uuid NOT NULL,
    active_from timestamptz NOT NULL,
    active_until timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false
);

CREATE INDEX IF NOT EXISTS idx_approval_delegate_active
    ON approval_delegates(approver_id, delegate_id, active_from, active_until)
    WHERE is_deleted = false;

CREATE TABLE IF NOT EXISTS approval_templates (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(120) NOT NULL UNIQUE,
    name varchar(255) NOT NULL,
    target_type varchar(100) NOT NULL,
    route_policy varchar(50) NOT NULL DEFAULT 'ROLE_BASED',
    approver_role varchar(120),
    approver_id uuid,
    sla_hours integer NOT NULL DEFAULT 24,
    escalation_hours integer NOT NULL DEFAULT 24,
    escalation_severity varchar(50) NOT NULL DEFAULT 'WARNING',
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false
);

CREATE INDEX IF NOT EXISTS idx_approval_templates_target
    ON approval_templates(target_type, active)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_approval_requests_expiration
    ON approval_requests(status, expires_at)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_approval_requests_escalation
    ON approval_requests(status, escalated_at, created_at)
    WHERE is_deleted = false;

CREATE UNIQUE INDEX IF NOT EXISTS ux_approval_requests_pending_target_action
    ON approval_requests(
        COALESCE(target_type, document_type),
        COALESCE(target_id, document_id),
        COALESCE(action_type, 'APPROVE')
    )
    WHERE is_deleted = false AND status = 'PENDING';

INSERT INTO approval_templates (code, name, target_type, route_policy, approver_role, sla_hours, escalation_hours, escalation_severity)
VALUES
    ('WORK_ORDER_APPROVAL', 'Work Order Approval', 'WORK_ORDER', 'ROLE_BASED', 'WORK_ORDER_APPROVER', 24, 24, 'WARNING'),
    ('BUDGET_APPROVAL', 'Budget Approval', 'MAINTENANCE_BUDGET', 'ROLE_BASED', 'BUDGET_APPROVER', 48, 48, 'WARNING'),
    ('PROCUREMENT_APPROVAL', 'Procurement Approval', 'PROCUREMENT_REQUEST', 'ROLE_BASED', 'PROCUREMENT_APPROVER', 48, 48, 'WARNING'),
    ('PPR_PLAN_APPROVAL', 'PPR Plan Approval', 'PPR_PLAN', 'ROLE_BASED', 'PPR_PLAN_APPROVER', 24, 24, 'WARNING'),
    ('MAINTENANCE_DUE_EVENT_APPROVAL', 'Maintenance Due Event Approval', 'MAINTENANCE_DUE_EVENT', 'ROLE_BASED', 'MAINTENANCE_EVENT_APPROVER', 12, 12, 'CRITICAL'),
    ('REPAIR_REQUEST_APPROVAL', 'Repair Request Approval', 'REPAIR_REQUEST', 'ROLE_BASED', 'REPAIR_REQUEST_APPROVER', 24, 24, 'WARNING'),
    ('MAINTENANCE_REGULATION_APPROVAL', 'Maintenance Regulation Approval', 'MAINTENANCE_REGULATION', 'ROLE_BASED', 'MAINTENANCE_REGULATION_APPROVER', 24, 24, 'WARNING')
ON CONFLICT (code) DO NOTHING;
