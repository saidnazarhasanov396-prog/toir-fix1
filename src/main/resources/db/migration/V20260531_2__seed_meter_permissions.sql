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

SELECT append_role_permissions('SYSTEM_ADMIN', ARRAY['*']);

SELECT append_role_permissions('TECHNICAL_DIRECTOR', ARRAY[
    'METER_READ',
    'METER_CREATE',
    'METER_UPDATE',
    'METER_DELETE',
    'METER_READING_CREATE',
    'METER_READING_DELETE'
]);

SELECT append_role_permissions('RELIABILITY_ENGINEER', ARRAY[
    'METER_READ',
    'METER_CREATE',
    'METER_UPDATE',
    'METER_READING_CREATE'
]);

SELECT append_role_permissions('PPR_ENGINEER', ARRAY[
    'METER_READ',
    'METER_READING_CREATE'
]);

SELECT append_role_permissions('FOREMAN', ARRAY[
    'METER_READ',
    'METER_READING_CREATE'
]);

SELECT append_role_permissions('CHIEF_MECHANIC', ARRAY[
    'METER_READ',
    'METER_READING_CREATE'
]);

SELECT append_role_permissions('CHIEF_POWER_ENGINEER', ARRAY[
    'METER_READ',
    'METER_READING_CREATE'
]);

SELECT append_role_permissions('CHIEF_INSTRUMENT_ENGINEER', ARRAY[
    'METER_READ',
    'METER_READING_CREATE'
]);

SELECT append_role_permissions('WORKSHOP_HEAD', ARRAY[
    'METER_READ',
    'METER_READING_CREATE'
]);

SELECT append_role_permissions('SECTION_HEAD', ARRAY[
    'METER_READ',
    'METER_READING_CREATE'
]);

SELECT append_role_permissions('STOREKEEPER', ARRAY[
    'METER_READ'
]);

SELECT append_role_permissions('INSPECTOR', ARRAY[
    'METER_READ',
    'METER_READING_CREATE'
]);

SELECT append_role_permissions('VIEWER', ARRAY[
    'METER_READ'
]);

DROP FUNCTION append_role_permissions(text, text[]);
