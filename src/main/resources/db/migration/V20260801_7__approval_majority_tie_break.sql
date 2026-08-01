ALTER TABLE approval_templates
    ADD COLUMN IF NOT EXISTS tie_break_policy varchar(32);

ALTER TABLE approval_templates
    DROP CONSTRAINT IF EXISTS approval_templates_tie_break_policy_check;

ALTER TABLE approval_templates
    ADD CONSTRAINT approval_templates_tie_break_policy_check
    CHECK (tie_break_policy IS NULL OR tie_break_policy IN (
        'APPROVE_ON_TIE',
        'REJECT_ON_TIE'
    ));

ALTER TABLE approval_requests
    ADD COLUMN IF NOT EXISTS tie_break_policy varchar(32);

ALTER TABLE approval_requests
    DROP CONSTRAINT IF EXISTS approval_requests_tie_break_policy_check;

ALTER TABLE approval_requests
    ADD CONSTRAINT approval_requests_tie_break_policy_check
    CHECK (tie_break_policy IS NULL OR tie_break_policy IN (
        'APPROVE_ON_TIE',
        'REJECT_ON_TIE'
    ));
