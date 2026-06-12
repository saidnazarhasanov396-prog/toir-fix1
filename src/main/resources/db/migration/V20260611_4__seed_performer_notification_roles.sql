CREATE OR REPLACE FUNCTION merge_role_permissions(role_code text, role_name text, role_name_en text, role_name_uz text, permissions_to_add text[])
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE
    existing_permissions jsonb;
BEGIN
    INSERT INTO roles (id, created_at, updated_at, is_deleted, is_system, code, name, name_en, name_uz, description, permissions)
    SELECT gen_random_uuid(), now(), now(), false, true, role_code, role_name, role_name_en, role_name_uz,
           'Performer role for assigned work order execution', '[]'::jsonb
    WHERE NOT EXISTS (
        SELECT 1 FROM roles WHERE code = role_code AND is_deleted = false
    );

    SELECT COALESCE(r.permissions, '[]'::jsonb)
    INTO existing_permissions
    FROM roles r
    WHERE r.code = role_code
      AND r.is_deleted = false
    LIMIT 1;

    UPDATE roles r
    SET permissions = (
            SELECT jsonb_agg(DISTINCT permission ORDER BY permission)
            FROM (
                SELECT jsonb_array_elements_text(existing_permissions) AS permission
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

SELECT merge_role_permissions('TECHNICIAN', 'Техник', 'Technician', 'Texnik', ARRAY[
    'read',
    'REPAIR_REQUEST_READ',
    'NOTIFICATION_READ',
    'NOTIFICATION_MARK_READ',
    'WORK_ORDER_READ',
    'WORK_ORDER_UPDATE',
    'WORK_ORDER_START',
    'WORK_ORDER_COMPLETE',
    'EQUIPMENT_READ',
    'KNOWLEDGE_READ'
]);

SELECT merge_role_permissions('MECHANIC', 'Механик', 'Mechanic', 'Mexanik', ARRAY[
    'read',
    'REPAIR_REQUEST_READ',
    'NOTIFICATION_READ',
    'NOTIFICATION_MARK_READ',
    'WORK_ORDER_READ',
    'WORK_ORDER_UPDATE',
    'WORK_ORDER_START',
    'WORK_ORDER_COMPLETE',
    'EQUIPMENT_READ',
    'KNOWLEDGE_READ'
]);

DROP FUNCTION merge_role_permissions(text, text, text, text, text[]);
