ALTER TABLE repair_campaigns
    ADD COLUMN scope_version bigint NOT NULL DEFAULT 0;

ALTER TABLE repair_campaigns
    ADD CONSTRAINT chk_repair_campaign_scope_version_non_negative CHECK (scope_version >= 0);

INSERT INTO roles (id, created_at, updated_at, is_deleted, is_system, code, name, name_en, name_uz,
                   description, permissions)
VALUES
    (gen_random_uuid(), now(), now(), false, true, 'REPAIR_CAMPAIGN_CHIEF_MECHANIC_APPROVER',
     'Chief mechanic campaign approver', 'Chief mechanic campaign approver', 'Bosh mexanik tasdiqlovchi',
     'Independent chief mechanic approval for repair campaign scope',
     '["REPAIR_CAMPAIGN_READ","REPAIR_CAMPAIGN_APPROVE"]'::jsonb),
    (gen_random_uuid(), now(), now(), false, true, 'REPAIR_CAMPAIGN_PRODUCTION_APPROVER',
     'Production campaign approver', 'Production campaign approver', 'Ishlab chiqarish tasdiqlovchi',
     'Independent production approval for repair campaign scope',
     '["REPAIR_CAMPAIGN_READ","REPAIR_CAMPAIGN_APPROVE"]'::jsonb),
    (gen_random_uuid(), now(), now(), false, true, 'REPAIR_CAMPAIGN_WAREHOUSE_APPROVER',
     'Warehouse campaign approver', 'Warehouse campaign approver', 'Ombor tasdiqlovchi',
     'Independent warehouse approval for repair campaign scope',
     '["REPAIR_CAMPAIGN_READ","REPAIR_CAMPAIGN_APPROVE"]'::jsonb),
    (gen_random_uuid(), now(), now(), false, true, 'REPAIR_CAMPAIGN_PROCUREMENT_APPROVER',
     'Procurement campaign approver', 'Procurement campaign approver', 'Taʼminot tasdiqlovchi',
     'Independent procurement approval for repair campaign scope',
     '["REPAIR_CAMPAIGN_READ","REPAIR_CAMPAIGN_APPROVE"]'::jsonb),
    (gen_random_uuid(), now(), now(), false, true, 'REPAIR_CAMPAIGN_FINANCE_APPROVER',
     'Finance campaign approver', 'Finance campaign approver', 'Moliya tasdiqlovchi',
     'Independent finance approval for repair campaign scope',
     '["REPAIR_CAMPAIGN_READ","REPAIR_CAMPAIGN_APPROVE"]'::jsonb),
    (gen_random_uuid(), now(), now(), false, true, 'REPAIR_CAMPAIGN_HSE_APPROVER',
     'HSE campaign approver', 'HSE campaign approver', 'HSE tasdiqlovchi',
     'Independent HSE approval for repair campaign scope',
     '["REPAIR_CAMPAIGN_READ","REPAIR_CAMPAIGN_APPROVE"]'::jsonb),
    (gen_random_uuid(), now(), now(), false, true, 'REPAIR_CAMPAIGN_CHIEF_ENGINEER_APPROVER',
     'Chief engineer campaign approver', 'Chief engineer campaign approver', 'Bosh muhandis tasdiqlovchi',
     'Independent chief engineer approval for repair campaign scope',
     '["REPAIR_CAMPAIGN_READ","REPAIR_CAMPAIGN_APPROVE"]'::jsonb),
    (gen_random_uuid(), now(), now(), false, true, 'MAINTENANCE_MANAGER',
     'Maintenance manager', 'Maintenance manager', 'Taʼmirlash menejeri',
     'Repair campaign planning manager',
     '["read","REPAIR_CAMPAIGN_READ","REPAIR_CAMPAIGN_CREATE","REPAIR_CAMPAIGN_MANAGE_WORK","REPAIR_CAMPAIGN_MANAGE_SCOPE","REPAIR_CAMPAIGN_MANAGE_SHUTDOWN_LINKS","REPAIR_CAMPAIGN_MANAGE_RESOURCES","REPAIR_CAMPAIGN_MANAGE_MATERIALS","REPAIR_CAMPAIGN_MANAGE_DEPENDENCIES","REPAIR_CAMPAIGN_REQUEST_APPROVAL","REPAIR_CAMPAIGN_GENERATE_WORK_ORDERS"]'::jsonb),
    (gen_random_uuid(), now(), now(), false, true, 'WAREHOUSE_MANAGER',
     'Warehouse manager', 'Warehouse manager', 'Ombor menejeri',
     'Repair campaign material manager',
     '["read","REPAIR_CAMPAIGN_READ","REPAIR_CAMPAIGN_MANAGE_MATERIALS"]'::jsonb)
ON CONFLICT (code) DO NOTHING;

INSERT INTO approval_templates (code, name, target_type, action_type, route_policy, approver_role,
                                sla_hours, escalation_hours, escalation_severity)
VALUES ('REPAIR_CAMPAIGN_APPROVAL', 'Repair Campaign Seven Discipline Approval',
        'REPAIR_CAMPAIGN', 'APPROVE', 'ROLE_BASED', NULL, 48, 24, 'CRITICAL')
ON CONFLICT (code) DO NOTHING;

WITH route(template_code, step_order, approver_role) AS (VALUES
    ('REPAIR_CAMPAIGN_APPROVAL', 1, 'REPAIR_CAMPAIGN_CHIEF_MECHANIC_APPROVER'),
    ('REPAIR_CAMPAIGN_APPROVAL', 2, 'REPAIR_CAMPAIGN_PRODUCTION_APPROVER'),
    ('REPAIR_CAMPAIGN_APPROVAL', 3, 'REPAIR_CAMPAIGN_WAREHOUSE_APPROVER'),
    ('REPAIR_CAMPAIGN_APPROVAL', 4, 'REPAIR_CAMPAIGN_PROCUREMENT_APPROVER'),
    ('REPAIR_CAMPAIGN_APPROVAL', 5, 'REPAIR_CAMPAIGN_FINANCE_APPROVER'),
    ('REPAIR_CAMPAIGN_APPROVAL', 6, 'REPAIR_CAMPAIGN_HSE_APPROVER'),
    ('REPAIR_CAMPAIGN_APPROVAL', 7, 'REPAIR_CAMPAIGN_CHIEF_ENGINEER_APPROVER')
)
INSERT INTO approval_template_steps (template_id, step_order, approver_id, approver_role)
SELECT template.id, route.step_order, NULL, route.approver_role
FROM route
JOIN approval_templates template ON template.code = route.template_code AND template.is_deleted = false
ON CONFLICT (template_id, step_order) DO UPDATE
SET approver_id = NULL, approver_role = EXCLUDED.approver_role, updated_at = now(), is_deleted = false;

CREATE OR REPLACE FUNCTION append_role_permissions(role_code text, permissions_to_add text[])
RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    UPDATE roles r SET permissions = (
        SELECT COALESCE(jsonb_agg(DISTINCT permission), '[]'::jsonb)
        FROM (
            SELECT jsonb_array_elements_text(CASE WHEN jsonb_typeof(COALESCE(r.permissions, '[]'::jsonb)) = 'array'
                THEN COALESCE(r.permissions, '[]'::jsonb) ELSE '[]'::jsonb END) AS permission
            UNION ALL SELECT unnest(permissions_to_add)
        ) merged_permissions
    ), updated_at = now()
    WHERE r.code = role_code AND r.is_deleted = false;
END;
$$;

SELECT append_role_permissions('SYSTEM_ADMIN', ARRAY[
    '*','REPAIR_CAMPAIGN_OUTBOX_READ','REPAIR_CAMPAIGN_OUTBOX_RETRY']);

SELECT append_role_permissions('TECHNICAL_DIRECTOR', ARRAY[
    'REPAIR_CAMPAIGN_READ','REPAIR_CAMPAIGN_CREATE','REPAIR_CAMPAIGN_UPDATE','REPAIR_CAMPAIGN_APPROVE',
    'REPAIR_CAMPAIGN_START','REPAIR_CAMPAIGN_SUSPEND','REPAIR_CAMPAIGN_COMPLETE','REPAIR_CAMPAIGN_CLOSE',
    'REPAIR_CAMPAIGN_CANCEL','REPAIR_CAMPAIGN_GENERATE_WORK_ORDERS','REPAIR_CAMPAIGN_MANAGE_WORK',
    'REPAIR_CAMPAIGN_MANAGE_SCOPE','REPAIR_CAMPAIGN_MANAGE_SHUTDOWN_LINKS','REPAIR_CAMPAIGN_MANAGE_RESOURCES',
    'REPAIR_CAMPAIGN_MANAGE_MATERIALS','REPAIR_CAMPAIGN_MANAGE_DEPENDENCIES','REPAIR_CAMPAIGN_MANAGE_FINANCE',
    'REPAIR_CAMPAIGN_REQUEST_APPROVAL','REPAIR_CAMPAIGN_RESUME','REPAIR_CAMPAIGN_BEGIN_CLOSING',
    'REPAIR_CAMPAIGN_ARCHIVE','REPAIR_CAMPAIGN_CONFIRM_DEFECT','REPAIR_CAMPAIGN_APPROVE_FX',
    'REPAIR_CAMPAIGN_APPROVE_BUDGET_OVERRUN','REPAIR_CAMPAIGN_APPROVE_CLOSURE']);

SELECT append_role_permissions('CHIEF_MECHANIC', ARRAY[
    'REPAIR_CAMPAIGN_READ','REPAIR_CAMPAIGN_CREATE','REPAIR_CAMPAIGN_UPDATE','REPAIR_CAMPAIGN_APPROVE',
    'REPAIR_CAMPAIGN_START','REPAIR_CAMPAIGN_SUSPEND','REPAIR_CAMPAIGN_COMPLETE','REPAIR_CAMPAIGN_CLOSE',
    'REPAIR_CAMPAIGN_CANCEL','REPAIR_CAMPAIGN_GENERATE_WORK_ORDERS','REPAIR_CAMPAIGN_MANAGE_WORK',
    'REPAIR_CAMPAIGN_MANAGE_SCOPE','REPAIR_CAMPAIGN_MANAGE_SHUTDOWN_LINKS','REPAIR_CAMPAIGN_MANAGE_RESOURCES',
    'REPAIR_CAMPAIGN_MANAGE_MATERIALS','REPAIR_CAMPAIGN_MANAGE_DEPENDENCIES','REPAIR_CAMPAIGN_MANAGE_FINANCE',
    'REPAIR_CAMPAIGN_REQUEST_APPROVAL','REPAIR_CAMPAIGN_RESUME','REPAIR_CAMPAIGN_BEGIN_CLOSING',
    'REPAIR_CAMPAIGN_ARCHIVE','REPAIR_CAMPAIGN_CONFIRM_DEFECT','REPAIR_CAMPAIGN_APPROVE_FX',
    'REPAIR_CAMPAIGN_APPROVE_BUDGET_OVERRUN','REPAIR_CAMPAIGN_APPROVE_CLOSURE']);

SELECT append_role_permissions('MAINTENANCE_MANAGER', ARRAY[
    'REPAIR_CAMPAIGN_READ','REPAIR_CAMPAIGN_CREATE','REPAIR_CAMPAIGN_MANAGE_WORK','REPAIR_CAMPAIGN_MANAGE_SCOPE',
    'REPAIR_CAMPAIGN_MANAGE_SHUTDOWN_LINKS','REPAIR_CAMPAIGN_MANAGE_RESOURCES','REPAIR_CAMPAIGN_MANAGE_MATERIALS',
    'REPAIR_CAMPAIGN_MANAGE_DEPENDENCIES','REPAIR_CAMPAIGN_REQUEST_APPROVAL','REPAIR_CAMPAIGN_GENERATE_WORK_ORDERS']);

SELECT append_role_permissions('WAREHOUSE_MANAGER', ARRAY[
    'REPAIR_CAMPAIGN_READ','REPAIR_CAMPAIGN_MANAGE_MATERIALS']);

SELECT append_role_permissions('FINANCE_MANAGER', ARRAY[
    'REPAIR_CAMPAIGN_READ','REPAIR_CAMPAIGN_MANAGE_FINANCE','REPAIR_CAMPAIGN_APPROVE_FX',
    'REPAIR_CAMPAIGN_APPROVE_BUDGET_OVERRUN']);

DROP FUNCTION append_role_permissions(text, text[]);
