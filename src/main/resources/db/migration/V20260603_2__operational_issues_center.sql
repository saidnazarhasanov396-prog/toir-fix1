UPDATE operational_issues
SET type = 'EQUIPMENT_LIFETIME_WARNING',
    updated_at = now()
WHERE type = 'EQUIPMENT_LIFETIME_EXPIRING'
  AND is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_operational_issues_equipment
    ON operational_issues (equipment_id);

CREATE INDEX IF NOT EXISTS idx_operational_issues_type
    ON operational_issues (type);

CREATE INDEX IF NOT EXISTS idx_operational_issues_severity
    ON operational_issues (severity);

CREATE INDEX IF NOT EXISTS idx_operational_issues_detected_at
    ON operational_issues (detected_at);

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
    'OPERATIONAL_ISSUE_READ',
    'OPERATIONAL_ISSUE_RESOLVE'
]);

SELECT append_role_permissions('DEPARTMENT_HEAD', ARRAY[
    'OPERATIONAL_ISSUE_READ',
    'OPERATIONAL_ISSUE_RESOLVE'
]);

DROP FUNCTION append_role_permissions(text, text[]);
