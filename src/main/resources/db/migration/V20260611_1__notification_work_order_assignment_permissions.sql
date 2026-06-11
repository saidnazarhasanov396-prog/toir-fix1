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

INSERT INTO roles (id, created_at, updated_at, is_deleted, is_system, code, name, name_en, name_uz, description, permissions)
SELECT gen_random_uuid(), now(), now(), false, true, 'TECHNICIAN', 'Техник', 'Technician', 'Texnik',
       'Operational maintenance performer', '[]'::jsonb
WHERE NOT EXISTS (
    SELECT 1 FROM roles WHERE code = 'TECHNICIAN' AND is_deleted = false
);

SELECT append_role_permissions('TECHNICIAN', ARRAY[
    'read',
    'REPAIR_REQUEST_READ',
    'WORK_ORDER_READ',
    'WORK_ORDER_UPDATE',
    'WORK_ORDER_START',
    'WORK_ORDER_COMPLETE',
    'EQUIPMENT_READ',
    'DEFECT_READ',
    'PPR_TASK_READ',
    'INSPECTION_READ',
    'INSPECTION_START',
    'INSPECTION_COMPLETE',
    'TIMESHEET_CREATE',
    'KNOWLEDGE_READ',
    'NOTIFICATION_READ',
    'NOTIFICATION_MARK_READ'
]);

SELECT append_role_permissions('FOREMAN', ARRAY[
    'WORK_ORDER_UPDATE',
    'NOTIFICATION_READ',
    'NOTIFICATION_MARK_READ'
]);

SELECT append_role_permissions('TECHNICAL_DIRECTOR', ARRAY[
    'WORK_ORDER_UPDATE',
    'NOTIFICATION_READ',
    'NOTIFICATION_MARK_READ',
    'NOTIFICATION_ADMIN'
]);

SELECT append_role_permissions('CHIEF_MECHANIC', ARRAY[
    'WORK_ORDER_UPDATE',
    'NOTIFICATION_READ',
    'NOTIFICATION_MARK_READ'
]);

SELECT append_role_permissions('CHIEF_POWER_ENGINEER', ARRAY[
    'WORK_ORDER_UPDATE',
    'NOTIFICATION_READ',
    'NOTIFICATION_MARK_READ'
]);

SELECT append_role_permissions('CHIEF_INSTRUMENT_ENGINEER', ARRAY[
    'WORK_ORDER_UPDATE',
    'NOTIFICATION_READ',
    'NOTIFICATION_MARK_READ'
]);

SELECT append_role_permissions('WORKSHOP_HEAD', ARRAY[
    'WORK_ORDER_UPDATE',
    'NOTIFICATION_READ',
    'NOTIFICATION_MARK_READ'
]);

SELECT append_role_permissions('SECTION_HEAD', ARRAY[
    'WORK_ORDER_UPDATE',
    'NOTIFICATION_READ',
    'NOTIFICATION_MARK_READ'
]);

SELECT append_role_permissions('DEPARTMENT_HEAD', ARRAY[
    'WORK_ORDER_UPDATE',
    'NOTIFICATION_READ',
    'NOTIFICATION_MARK_READ'
]);

DROP FUNCTION append_role_permissions(text, text[]);
