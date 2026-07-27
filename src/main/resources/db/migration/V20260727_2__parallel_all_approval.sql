ALTER TABLE approval_templates
    ADD COLUMN IF NOT EXISTS flow_type varchar(32),
    ADD COLUMN IF NOT EXISTS version bigint;

UPDATE approval_templates
SET flow_type = 'SEQUENTIAL'
WHERE flow_type IS NULL;

UPDATE approval_templates
SET version = 0
WHERE version IS NULL;

ALTER TABLE approval_templates
    ALTER COLUMN flow_type SET DEFAULT 'SEQUENTIAL',
    ALTER COLUMN flow_type SET NOT NULL,
    ALTER COLUMN version SET DEFAULT 0,
    ALTER COLUMN version SET NOT NULL;

ALTER TABLE approval_templates
    DROP CONSTRAINT IF EXISTS approval_templates_flow_type_check;

ALTER TABLE approval_templates
    ADD CONSTRAINT approval_templates_flow_type_check
    CHECK (flow_type IN ('SEQUENTIAL', 'PARALLEL_ALL'));

ALTER TABLE approval_requests
    ADD COLUMN IF NOT EXISTS flow_type varchar(32),
    ADD COLUMN IF NOT EXISTS approval_round integer,
    ADD COLUMN IF NOT EXISTS template_id uuid,
    ADD COLUMN IF NOT EXISTS template_version bigint;

UPDATE approval_requests
SET flow_type = 'SEQUENTIAL'
WHERE flow_type IS NULL;

UPDATE approval_requests
SET approval_round = 1
WHERE approval_round IS NULL;

ALTER TABLE approval_requests
    ALTER COLUMN flow_type SET DEFAULT 'SEQUENTIAL',
    ALTER COLUMN flow_type SET NOT NULL,
    ALTER COLUMN approval_round SET DEFAULT 1,
    ALTER COLUMN approval_round SET NOT NULL;

ALTER TABLE approval_requests
    DROP CONSTRAINT IF EXISTS approval_requests_flow_type_check;

ALTER TABLE approval_requests
    ADD CONSTRAINT approval_requests_flow_type_check
    CHECK (flow_type IN ('SEQUENTIAL', 'PARALLEL_ALL'));

ALTER TABLE approval_requests
    DROP CONSTRAINT IF EXISTS approval_requests_round_check;

ALTER TABLE approval_requests
    ADD CONSTRAINT approval_requests_round_check
    CHECK (approval_round > 0);

ALTER TABLE approval_steps
    ADD COLUMN IF NOT EXISTS approval_round integer,
    ADD COLUMN IF NOT EXISTS flow_type varchar(32);

UPDATE approval_steps step
SET approval_round = COALESCE(request.approval_round, 1)
FROM approval_requests request
WHERE request.id = step.request_id
  AND step.approval_round IS NULL;

UPDATE approval_steps
SET approval_round = 1
WHERE approval_round IS NULL;

UPDATE approval_steps step
SET flow_type = COALESCE(request.flow_type, 'SEQUENTIAL')
FROM approval_requests request
WHERE request.id = step.request_id
  AND step.flow_type IS NULL;

UPDATE approval_steps
SET flow_type = 'SEQUENTIAL'
WHERE flow_type IS NULL;

ALTER TABLE approval_steps
    ALTER COLUMN approval_round SET DEFAULT 1,
    ALTER COLUMN approval_round SET NOT NULL,
    ALTER COLUMN flow_type SET DEFAULT 'SEQUENTIAL',
    ALTER COLUMN flow_type SET NOT NULL;

ALTER TABLE approval_steps
    DROP CONSTRAINT IF EXISTS approval_steps_round_check;

ALTER TABLE approval_steps
    ADD CONSTRAINT approval_steps_round_check
    CHECK (approval_round > 0);

ALTER TABLE approval_steps
    DROP CONSTRAINT IF EXISTS approval_steps_flow_type_check;

ALTER TABLE approval_steps
    ADD CONSTRAINT approval_steps_flow_type_check
    CHECK (flow_type IN ('SEQUENTIAL', 'PARALLEL_ALL'));

ALTER TABLE approval_steps
    DROP CONSTRAINT IF EXISTS approval_steps_decision_check;

ALTER TABLE approval_steps
    ADD CONSTRAINT approval_steps_decision_check
    CHECK (decision IN (
        'PENDING',
        'APPROVED',
        'REJECTED',
        'CANCELLED',
        'RETURNED'
    ));

ALTER TABLE approval_steps
    DROP CONSTRAINT IF EXISTS uq_approval_step_round_assignee;

CREATE UNIQUE INDEX IF NOT EXISTS uq_approval_step_round_assignee
    ON approval_steps (request_id, approval_round, approver_id)
    WHERE flow_type = 'PARALLEL_ALL'
      AND approver_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_approval_requests_target_round
    ON approval_requests (
        COALESCE(target_type, document_type),
        COALESCE(target_id, document_id),
        COALESCE(action_type, 'APPROVE'),
        approval_round DESC
    )
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_approval_steps_request_round_decision
    ON approval_steps (request_id, approval_round, decision)
    WHERE is_deleted = false;
