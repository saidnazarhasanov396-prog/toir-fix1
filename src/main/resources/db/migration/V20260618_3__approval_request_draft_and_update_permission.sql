ALTER TABLE approval_requests
    DROP CONSTRAINT IF EXISTS approval_requests_status_check;

ALTER TABLE approval_requests
    ADD CONSTRAINT approval_requests_status_check
    CHECK (status IN (
        'DRAFT',
        'PENDING',
        'APPROVED',
        'REJECTED',
        'CANCELLED',
        'EXPIRED',
        'FAILED'
    ));

UPDATE roles
SET permissions = (
    SELECT jsonb_agg(DISTINCT permission)
    FROM (
        SELECT jsonb_array_elements_text(COALESCE(roles.permissions, '[]'::jsonb)) AS permission
        UNION ALL
        SELECT 'APPROVAL_UPDATE'
    ) permissions
)
WHERE code = 'FINANCE_MANAGER'
  AND is_deleted = false;
