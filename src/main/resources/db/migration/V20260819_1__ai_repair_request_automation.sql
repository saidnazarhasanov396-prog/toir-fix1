ALTER TABLE repair_requests
    DROP CONSTRAINT IF EXISTS repair_requests_source_check;

ALTER TABLE repair_requests
    ADD CONSTRAINT repair_requests_source_check
        CHECK (source IN (
            'MANUAL',
            'OPERATOR',
            'SCADA',
            'INSPECTION',
            'MOBILE',
            'AI'
        ));

ALTER TABLE repair_requests
    ADD COLUMN IF NOT EXISTS ai_problem_key varchar(255);

CREATE INDEX IF NOT EXISTS idx_repair_requests_equipment_ai_problem_key
    ON repair_requests (equipment_id, ai_problem_key)
    WHERE is_deleted = false AND ai_problem_key IS NOT NULL;

CREATE TABLE IF NOT EXISTS ai_analysis_runs (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,
    kind varchar(64) NOT NULL,
    equipment_id uuid,
    work_order_id uuid,
    reporter_id uuid,
    job_id uuid,
    media_sha256 varchar(64),
    problem_key varchar(255),
    action varchar(32) NOT NULL,
    skipped_reason varchar(64),
    repair_request_id uuid,
    payload text,
    CONSTRAINT fk_ai_analysis_runs_equipment
        FOREIGN KEY (equipment_id) REFERENCES equipment (id),
    CONSTRAINT fk_ai_analysis_runs_work_order
        FOREIGN KEY (work_order_id) REFERENCES work_orders (id),
    CONSTRAINT fk_ai_analysis_runs_repair_request
        FOREIGN KEY (repair_request_id) REFERENCES repair_requests (id),
    CONSTRAINT chk_ai_analysis_runs_kind
        CHECK (kind IN (
            'VISUAL_INSPECTION',
            'WORK_ORDER_DRAFT',
            'CAUSE_REPAIR',
            'FAILURE_EVIDENCE'
        )),
    CONSTRAINT chk_ai_analysis_runs_action
        CHECK (action IN ('CREATED', 'UPDATED', 'APPENDED', 'SKIPPED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_analysis_runs_job_id
    ON ai_analysis_runs (job_id)
    WHERE job_id IS NOT NULL AND is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_ai_analysis_runs_equipment_problem
    ON ai_analysis_runs (equipment_id, problem_key, created_at DESC)
    WHERE is_deleted = false;

CREATE TABLE IF NOT EXISTS ai_job_contexts (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,
    job_id uuid NOT NULL,
    kind varchar(64) NOT NULL,
    equipment_id uuid,
    work_order_id uuid,
    reporter_id uuid,
    media_sha256 varchar(64),
    processed_at timestamptz,
    repair_request_id uuid,
    action varchar(32),
    skipped_reason varchar(64),
    CONSTRAINT uq_ai_job_contexts_job_id UNIQUE (job_id),
    CONSTRAINT fk_ai_job_contexts_equipment
        FOREIGN KEY (equipment_id) REFERENCES equipment (id),
    CONSTRAINT fk_ai_job_contexts_work_order
        FOREIGN KEY (work_order_id) REFERENCES work_orders (id),
    CONSTRAINT fk_ai_job_contexts_repair_request
        FOREIGN KEY (repair_request_id) REFERENCES repair_requests (id),
    CONSTRAINT chk_ai_job_contexts_kind
        CHECK (kind IN (
            'VISUAL_INSPECTION',
            'WORK_ORDER_DRAFT',
            'CAUSE_REPAIR',
            'FAILURE_EVIDENCE'
        )),
    CONSTRAINT chk_ai_job_contexts_action
        CHECK (action IS NULL OR action IN ('CREATED', 'UPDATED', 'APPENDED', 'SKIPPED'))
);

CREATE INDEX IF NOT EXISTS idx_ai_job_contexts_equipment
    ON ai_job_contexts (equipment_id)
    WHERE is_deleted = false;
