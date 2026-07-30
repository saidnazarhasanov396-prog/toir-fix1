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
                     CASE WHEN jsonb_typeof(COALESCE(r.permissions, '[]'::jsonb)) = 'array'
                          THEN COALESCE(r.permissions, '[]'::jsonb) ELSE '[]'::jsonb END) AS permission
                 UNION ALL
                 SELECT unnest(permissions_to_add)
             ) merged
            WHERE permission IS NOT NULL AND btrim(permission) <> ''
       ), updated_at = now()
     WHERE r.code = role_code AND r.is_deleted = false;
END;
$$;

SELECT append_role_permissions('SYSTEM_ADMIN', ARRAY[
    'SPARE_PART_MANUAL_DUE',
    'SPARE_PART_DUE_WORK_ORDER_CREATE',
    'SPARE_PART_LIFECYCLE_OVERRIDE'
]);

SELECT append_role_permissions('MAINTENANCE_MANAGER', ARRAY[
    'SPARE_PART_MANUAL_DUE',
    'SPARE_PART_DUE_WORK_ORDER_CREATE'
]);

SELECT append_role_permissions('MAINTENANCE_ENGINEER', ARRAY[
    'SPARE_PART_MANUAL_DUE',
    'SPARE_PART_DUE_WORK_ORDER_CREATE'
]);

DROP FUNCTION append_role_permissions(text, text[]);
