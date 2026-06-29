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

SELECT append_role_permissions('ECONOMIST', ARRAY[
    'BUDGET_TRANSFER',
    'BUDGET_REVISE',
    'ACTUAL_COST_ALLOCATE',
    'ACTUAL_COST_REQUEST_CORRECTION',
    'FINANCE_REPORT_EXPORT'
]);

SELECT append_role_permissions('FINANCE_MANAGER', ARRAY[
    'BUDGET_CLOSE',
    'BUDGET_REOPEN',
    'BUDGET_TRANSFER',
    'BUDGET_REVISE',
    'ACTUAL_COST_ALLOCATE',
    'ACTUAL_COST_REQUEST_CORRECTION',
    'FINANCE_REPORT_EXPORT'
]);

DROP FUNCTION append_role_permissions(text, text[]);
