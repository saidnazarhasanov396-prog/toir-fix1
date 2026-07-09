INSERT INTO roles (
    id,
    created_at,
    updated_at,
    is_deleted,
    is_system,
    code,
    name,
    name_en,
    name_uz,
    description,
    permissions
)
VALUES (
    gen_random_uuid(),
    now(),
    now(),
    false,
    true,
    'WAREHOUSE_MANAGER',
    'Warehouse Manager',
    'Warehouse Manager',
    'Ombor mudiri',
    'Approves warehouse stock operations and write-offs',
    '["WAREHOUSE_READ","WAREHOUSE_BIN_READ","WAREHOUSE_TASK_READ","WAREHOUSE_WRITEOFF_REQUEST","WAREHOUSE_WRITEOFF_APPROVE","STOCK_READ","WAREHOUSE_ANALYTICS_READ"]'::jsonb
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO approval_templates (
    code,
    name,
    target_type,
    action_type,
    route_policy,
    approver_role,
    sla_hours,
    escalation_hours,
    escalation_severity
)
VALUES (
    'WAREHOUSE_WRITEOFF_APPROVAL',
    'Warehouse Write-Off Approval',
    'WAREHOUSE_WRITEOFF',
    'APPROVE',
    'ROLE_BASED',
    'WAREHOUSE_MANAGER',
    24,
    24,
    'WARNING'
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO approval_template_steps (template_id, step_order, approver_id, approver_role)
SELECT template.id, 1, NULL, 'WAREHOUSE_MANAGER'
FROM approval_templates template
WHERE template.code = 'WAREHOUSE_WRITEOFF_APPROVAL'
  AND template.is_deleted = false
ON CONFLICT (template_id, step_order) DO NOTHING;

UPDATE roles
SET permissions = (
        SELECT jsonb_agg(DISTINCT permission ORDER BY permission)
        FROM jsonb_array_elements_text(
            COALESCE(permissions, '[]'::jsonb)
            || '["WAREHOUSE_WRITEOFF_APPROVE"]'::jsonb
        ) AS permission
    ),
    updated_at = now()
WHERE code IN ('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER')
  AND is_deleted = false;
