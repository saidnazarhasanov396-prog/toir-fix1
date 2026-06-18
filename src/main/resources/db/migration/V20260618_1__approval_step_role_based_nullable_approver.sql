ALTER TABLE approval_steps
    ALTER COLUMN approver_id DROP NOT NULL;

ALTER TABLE approval_steps
    DROP CONSTRAINT IF EXISTS approval_steps_approver_required_check;

ALTER TABLE approval_steps
    ADD CONSTRAINT approval_steps_approver_required_check
        CHECK (
            approver_id IS NOT NULL
            OR NULLIF(BTRIM(approver_role), '') IS NOT NULL
        );
