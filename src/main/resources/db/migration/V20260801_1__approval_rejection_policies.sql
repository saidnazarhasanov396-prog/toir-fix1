ALTER TABLE approval_templates
    ADD COLUMN IF NOT EXISTS rejection_policy varchar(48);

UPDATE approval_templates
SET rejection_policy = 'TERMINATE'
WHERE rejection_policy IS NULL;

ALTER TABLE approval_templates
    ALTER COLUMN rejection_policy SET DEFAULT 'TERMINATE',
    ALTER COLUMN rejection_policy SET NOT NULL;

ALTER TABLE approval_templates
    DROP CONSTRAINT IF EXISTS approval_templates_rejection_policy_check;

ALTER TABLE approval_templates
    ADD CONSTRAINT approval_templates_rejection_policy_check
    CHECK (rejection_policy IN (
        'TERMINATE',
        'RETURN_TO_PREVIOUS_STEP',
        'RETURN_TO_INITIATOR',
        'MAJORITY'
    ));

ALTER TABLE approval_requests
    ADD COLUMN IF NOT EXISTS rejection_policy varchar(48);

UPDATE approval_requests
SET rejection_policy = 'TERMINATE'
WHERE rejection_policy IS NULL;

ALTER TABLE approval_requests
    ALTER COLUMN rejection_policy SET DEFAULT 'TERMINATE',
    ALTER COLUMN rejection_policy SET NOT NULL;

ALTER TABLE approval_requests
    DROP CONSTRAINT IF EXISTS approval_requests_rejection_policy_check;

ALTER TABLE approval_requests
    ADD CONSTRAINT approval_requests_rejection_policy_check
    CHECK (rejection_policy IN (
        'TERMINATE',
        'RETURN_TO_PREVIOUS_STEP',
        'RETURN_TO_INITIATOR',
        'MAJORITY'
    ));

ALTER TABLE approval_requests
    DROP CONSTRAINT IF EXISTS approval_requests_status_check;

ALTER TABLE approval_requests
    ADD CONSTRAINT approval_requests_status_check
    CHECK (status IN (
        'DRAFT',
        'PENDING',
        'APPROVED',
        'REJECTED',
        'REWORK',
        'CANCELLED',
        'EXPIRED',
        'FAILED',
        'SUPERSEDED'
    ));

ALTER TABLE approval_history
    ADD COLUMN IF NOT EXISTS step_id uuid,
    ADD COLUMN IF NOT EXISTS step_number integer,
    ADD COLUMN IF NOT EXISTS approval_round integer,
    ADD COLUMN IF NOT EXISTS decision varchar(32);

ALTER TABLE approval_history
    DROP CONSTRAINT IF EXISTS approval_history_decision_check;

ALTER TABLE approval_history
    ADD CONSTRAINT approval_history_decision_check
    CHECK (decision IS NULL OR decision IN (
        'PENDING',
        'APPROVED',
        'REJECTED',
        'CANCELLED',
        'RETURNED'
    ));

CREATE INDEX IF NOT EXISTS idx_approval_history_request_round
    ON approval_history (approval_id, approval_round, changed_at)
    WHERE is_deleted = false;
