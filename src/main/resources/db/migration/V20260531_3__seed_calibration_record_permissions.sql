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
    'CALIBRATION_RECORD_READ',
    'CALIBRATION_RECORD_CREATE',
    'CALIBRATION_RECORD_UPDATE',
    'CALIBRATION_RECORD_DELETE'
]);

SELECT append_role_permissions('PPR_ENGINEER', ARRAY[
    'CALIBRATION_RECORD_READ',
    'CALIBRATION_RECORD_CREATE',
    'CALIBRATION_RECORD_UPDATE'
]);

SELECT append_role_permissions('RELIABILITY_ENGINEER', ARRAY[
    'CALIBRATION_RECORD_READ',
    'CALIBRATION_RECORD_CREATE',
    'CALIBRATION_RECORD_UPDATE'
]);

SELECT append_role_permissions('FOREMAN', ARRAY[
    'CALIBRATION_RECORD_READ',
    'CALIBRATION_RECORD_CREATE',
    'CALIBRATION_RECORD_UPDATE'
]);

SELECT append_role_permissions('CHIEF_MECHANIC', ARRAY[
    'CALIBRATION_RECORD_READ'
]);

SELECT append_role_permissions('CHIEF_POWER_ENGINEER', ARRAY[
    'CALIBRATION_RECORD_READ'
]);

SELECT append_role_permissions('CHIEF_INSTRUMENT_ENGINEER', ARRAY[
    'CALIBRATION_RECORD_READ'
]);

SELECT append_role_permissions('WORKSHOP_HEAD', ARRAY[
    'CALIBRATION_RECORD_READ',
    'CALIBRATION_RECORD_CREATE',
    'CALIBRATION_RECORD_UPDATE'
]);

SELECT append_role_permissions('SECTION_HEAD', ARRAY[
    'CALIBRATION_RECORD_READ',
    'CALIBRATION_RECORD_CREATE',
    'CALIBRATION_RECORD_UPDATE'
]);

SELECT append_role_permissions('STOREKEEPER', ARRAY[
    'CALIBRATION_RECORD_READ'
]);

SELECT append_role_permissions('INSPECTOR', ARRAY[
    'CALIBRATION_RECORD_READ',
    'CALIBRATION_RECORD_CREATE',
    'CALIBRATION_RECORD_UPDATE'
]);

SELECT append_role_permissions('VIEWER', ARRAY[
    'CALIBRATION_RECORD_READ'
]);

DROP FUNCTION append_role_permissions(text, text[]);
