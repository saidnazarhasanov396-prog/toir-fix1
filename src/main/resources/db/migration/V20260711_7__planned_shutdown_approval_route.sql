INSERT INTO roles (id, created_at, updated_at, is_deleted, is_system, code, name, name_en, name_uz,
                   description, permissions)
VALUES
    (gen_random_uuid(), now(), now(), false, true, 'PLANNED_SHUTDOWN_PRODUCTION_APPROVER',
     'Production shutdown approver', 'Production shutdown approver', 'Ishlab chiqarishni to‘xtatish tasdiqlovchisi',
     'Independent production approval for planned shutdown scope',
     '["PLANNED_SHUTDOWN_READ","PLANNED_SHUTDOWN_APPROVE"]'::jsonb),
    (gen_random_uuid(), now(), now(), false, true, 'PLANNED_SHUTDOWN_HSE_APPROVER',
     'HSE shutdown approver', 'HSE shutdown approver', 'HSE to‘xtatish tasdiqlovchisi',
     'Independent HSE approval for planned shutdown scope',
     '["PLANNED_SHUTDOWN_READ","PLANNED_SHUTDOWN_APPROVE"]'::jsonb)
ON CONFLICT (code) DO NOTHING;

INSERT INTO approval_templates (code, name, target_type, action_type, route_policy, approver_role,
                                sla_hours, escalation_hours, escalation_severity)
VALUES ('PLANNED_SHUTDOWN_APPROVAL', 'Planned Shutdown Production and HSE Approval',
        'PLANNED_SHUTDOWN', 'APPROVE', 'ROLE_BASED', NULL, 24, 12, 'CRITICAL')
ON CONFLICT (code) DO NOTHING;

INSERT INTO approval_template_steps (template_id, step_order, approver_id, approver_role)
SELECT template.id, step.step_order, NULL, step.approver_role
FROM approval_templates template
CROSS JOIN (VALUES
    (1, 'PLANNED_SHUTDOWN_PRODUCTION_APPROVER'),
    (2, 'PLANNED_SHUTDOWN_HSE_APPROVER')
) AS step(step_order, approver_role)
WHERE template.code = 'PLANNED_SHUTDOWN_APPROVAL' AND template.is_deleted = false
ON CONFLICT (template_id, step_order) DO UPDATE
SET approver_id = NULL, approver_role = EXCLUDED.approver_role, updated_at = now(), is_deleted = false;
