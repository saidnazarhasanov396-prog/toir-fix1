CREATE TABLE IF NOT EXISTS work_order_completion_evidence (
    id uuid PRIMARY KEY,
    work_order_id uuid NOT NULL,
    ppr_task_id uuid,
    evidence_type varchar(32) NOT NULL,
    file_asset_id uuid,
    captured_at timestamptz NOT NULL,
    submitted_by_id uuid,
    note text,
    is_deleted boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_work_order_completion_evidence_work_order
        FOREIGN KEY (work_order_id) REFERENCES work_orders(id),
    CONSTRAINT fk_work_order_completion_evidence_ppr_task
        FOREIGN KEY (ppr_task_id) REFERENCES ppr_tasks(id),
    CONSTRAINT fk_work_order_completion_evidence_file
        FOREIGN KEY (file_asset_id) REFERENCES file_assets(id),
    CONSTRAINT ck_work_order_completion_evidence_type
        CHECK (evidence_type IN (
            'BEFORE_PHOTO',
            'AFTER_PHOTO',
            'MEASUREMENT',
            'DOCUMENT',
            'REPAIR_ACT',
            'STOPPAGE_ACT',
            'OTHER'
        ))
);

CREATE INDEX IF NOT EXISTS idx_work_order_completion_evidence_work_order
    ON work_order_completion_evidence (work_order_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_work_order_completion_evidence_ppr_task
    ON work_order_completion_evidence (ppr_task_id)
    WHERE is_deleted = false;

CREATE UNIQUE INDEX IF NOT EXISTS uq_work_order_completion_evidence_file
    ON work_order_completion_evidence (work_order_id, evidence_type, file_asset_id)
    WHERE file_asset_id IS NOT NULL AND is_deleted = false;
