ALTER TABLE approval_steps
    DROP CONSTRAINT IF EXISTS approval_steps_decision_check;

ALTER TABLE approval_steps
    ADD CONSTRAINT approval_steps_decision_check
    CHECK (decision IN (
        'PENDING',
        'APPROVED',
        'REJECTED',
        'RETURNED'
    ));

ALTER TABLE approval_requests
    ADD COLUMN IF NOT EXISTS last_returned_at timestamptz,
    ADD COLUMN IF NOT EXISTS last_returned_by uuid,
    ADD COLUMN IF NOT EXISTS last_return_comment text;

ALTER TABLE approval_history
    ADD COLUMN IF NOT EXISTS delegated_for_id uuid;

CREATE OR REPLACE FUNCTION append_role_permissions(role_code text, permissions_to_add text[])
RETURNS void AS $$
BEGIN
    UPDATE roles r
    SET permissions = (
        SELECT jsonb_agg(DISTINCT permission)
        FROM (
            SELECT jsonb_array_elements_text(COALESCE(r.permissions, '[]'::jsonb)) AS permission
            UNION
            SELECT unnest(permissions_to_add) AS permission
        ) merged_permissions
    )
    WHERE r.code = role_code
      AND r.is_deleted = false;
END;
$$ LANGUAGE plpgsql;

SELECT append_role_permissions('FINANCE_MANAGER', ARRAY['APPROVAL_RETURN']);

DROP FUNCTION append_role_permissions(text, text[]);
