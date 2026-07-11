-- Conservative request-approval matrix:
-- SYSTEM_ADMIN, TECHNICAL_DIRECTOR, CHIEF_MECHANIC already hold both shutdown UPDATE and APPROVE.
-- PPR_ENGINEER is intentionally excluded: UPDATE alone must not imply authority to submit an approval snapshot.
-- RELIABILITY_ENGINEER remains read-oriented and is also intentionally excluded.
UPDATE roles role
SET permissions = (
        SELECT COALESCE(jsonb_agg(DISTINCT permission ORDER BY permission), '[]'::jsonb)
        FROM (
            SELECT jsonb_array_elements_text(
                    CASE WHEN jsonb_typeof(COALESCE(role.permissions, '[]'::jsonb)) = 'array'
                         THEN COALESCE(role.permissions, '[]'::jsonb) ELSE '[]'::jsonb END) AS permission
            UNION ALL
            SELECT 'PLANNED_SHUTDOWN_REQUEST_APPROVAL'
        ) merged
    ),
    updated_at = now()
WHERE role.code IN ('SYSTEM_ADMIN', 'TECHNICAL_DIRECTOR', 'CHIEF_MECHANIC')
  AND role.is_deleted = false;
