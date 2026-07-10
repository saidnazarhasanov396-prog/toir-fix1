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

SELECT append_role_permissions('SYSTEM_ADMIN', ARRAY[
    'SPARE_PART_LIFE_RULE_READ',
    'SPARE_PART_LIFE_RULE_CREATE',
    'SPARE_PART_LIFE_RULE_UPDATE',
    'SPARE_PART_LIFE_RULE_DELETE',
    'SPARE_PART_INSTALLATION_READ',
    'SPARE_PART_INSTALL',
    'SPARE_PART_REMOVE',
    'SPARE_PART_REPLACE',
    'SPARE_PART_DUE_READ',
    'SPARE_PART_DUE_ACKNOWLEDGE',
    'SPARE_PART_EXPIRY_OVERRIDE'
]);

SELECT append_role_permissions('MAINTENANCE_MANAGER', ARRAY[
    'SPARE_PART_LIFE_RULE_READ',
    'SPARE_PART_LIFE_RULE_CREATE',
    'SPARE_PART_LIFE_RULE_UPDATE',
    'SPARE_PART_LIFE_RULE_DELETE',
    'SPARE_PART_INSTALLATION_READ',
    'SPARE_PART_INSTALL',
    'SPARE_PART_REMOVE',
    'SPARE_PART_REPLACE',
    'SPARE_PART_DUE_READ',
    'SPARE_PART_DUE_ACKNOWLEDGE',
    'SPARE_PART_EXPIRY_OVERRIDE'
]);

SELECT append_role_permissions('MAINTENANCE_ENGINEER', ARRAY[
    'SPARE_PART_LIFE_RULE_READ',
    'SPARE_PART_INSTALLATION_READ',
    'SPARE_PART_INSTALL',
    'SPARE_PART_REMOVE',
    'SPARE_PART_REPLACE',
    'SPARE_PART_DUE_READ',
    'SPARE_PART_DUE_ACKNOWLEDGE'
]);

DROP FUNCTION append_role_permissions(text, text[]);
