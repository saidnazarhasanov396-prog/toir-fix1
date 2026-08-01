ALTER TABLE ppr_planning_sessions
    ADD COLUMN selected_variant_revision BIGINT,
    ADD COLUMN selected_variant_hash VARCHAR(64),
    ADD COLUMN selected_variant_hash_version INTEGER,
    ADD COLUMN approval_request_id UUID;

ALTER TABLE approval_requests
    ADD COLUMN ppr_planning_variant_id UUID;

ALTER TABLE ppr_plans
    ADD COLUMN planning_session_id UUID,
    ADD COLUMN source_variant_id UUID,
    ADD COLUMN source_variant_revision BIGINT;

ALTER TABLE ppr_tasks
    ADD COLUMN source_variant_item_id UUID,
    ADD COLUMN work_order_lead_days INTEGER NOT NULL DEFAULT 7;

ALTER TABLE ppr_planning_sessions
    ADD CONSTRAINT chk_ppr_planning_session_selected_tuple
        CHECK (
            (selected_variant_revision IS NULL
                AND selected_variant_hash IS NULL
                AND selected_variant_hash_version IS NULL)
            OR
            (selected_variant_revision >= 1
                AND selected_variant_hash ~ '^[0-9a-f]{64}$'
                AND selected_variant_hash_version >= 1)
        ),
    ADD CONSTRAINT fk_ppr_planning_session_approval_request
        FOREIGN KEY (approval_request_id)
        REFERENCES approval_requests(id)
        ON DELETE RESTRICT;

ALTER TABLE approval_requests
    ADD CONSTRAINT fk_approval_request_ppr_planning_variant
        FOREIGN KEY (ppr_planning_variant_id)
        REFERENCES ppr_planning_variants(id)
        ON DELETE RESTRICT,
    ADD CONSTRAINT chk_approval_request_ppr_planning_binding
        CHECK (
            COALESCE(target_type, document_type) <> 'PPR_PLANNING_SESSION'
            OR (
                ppr_planning_variant_id IS NOT NULL
                AND calculation_revision >= 1
                AND calculation_content_hash ~ '^[0-9a-f]{64}$'
                AND calculation_content_hash_version >= 1
            )
        );

ALTER TABLE ppr_plans
    ADD CONSTRAINT fk_ppr_plan_planning_session
        FOREIGN KEY (planning_session_id)
        REFERENCES ppr_planning_sessions(id)
        ON DELETE RESTRICT,
    ADD CONSTRAINT fk_ppr_plan_source_variant
        FOREIGN KEY (source_variant_id)
        REFERENCES ppr_planning_variants(id)
        ON DELETE RESTRICT,
    ADD CONSTRAINT chk_ppr_plan_source_variant_tuple
        CHECK (
            (planning_session_id IS NULL
                AND source_variant_id IS NULL
                AND source_variant_revision IS NULL)
            OR
            (planning_session_id IS NOT NULL
                AND source_variant_id IS NOT NULL
                AND source_variant_revision >= 1)
        );

ALTER TABLE ppr_tasks
    ADD CONSTRAINT fk_ppr_task_source_variant_item
        FOREIGN KEY (source_variant_item_id)
        REFERENCES ppr_planning_variant_items(id)
        ON DELETE RESTRICT,
    ADD CONSTRAINT chk_ppr_task_work_order_lead_days
        CHECK (work_order_lead_days BETWEEN 0 AND 365);

CREATE UNIQUE INDEX uq_ppr_plans_planning_session
    ON ppr_plans(planning_session_id)
    WHERE planning_session_id IS NOT NULL;

CREATE UNIQUE INDEX uq_ppr_tasks_source_variant_item
    ON ppr_tasks(source_variant_item_id)
    WHERE source_variant_item_id IS NOT NULL;

CREATE UNIQUE INDEX uq_ppr_planning_active_approval
    ON approval_requests((COALESCE(target_id, document_id)))
    WHERE is_deleted = FALSE
      AND status = 'PENDING'
      AND COALESCE(target_type, document_type) = 'PPR_PLANNING_SESSION';

CREATE TABLE ppr_task_required_evidence (
    ppr_task_id UUID NOT NULL,
    evidence_type VARCHAR(32) NOT NULL,
    PRIMARY KEY (ppr_task_id, evidence_type),
    CONSTRAINT fk_ppr_task_required_evidence_task
        FOREIGN KEY (ppr_task_id)
        REFERENCES ppr_tasks(id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_ppr_task_required_evidence_type
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
