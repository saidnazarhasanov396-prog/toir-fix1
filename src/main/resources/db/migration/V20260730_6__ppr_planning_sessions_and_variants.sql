CREATE TABLE ppr_planning_sessions (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    planning_year INTEGER NOT NULL,
    department_id UUID NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    notes TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    selected_variant_id UUID,
    approved_plan_id UUID,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_ppr_planning_session_year
        CHECK (planning_year BETWEEN 2000 AND 2200),
    CONSTRAINT chk_ppr_planning_session_dates
        CHECK (start_date <= end_date),
    CONSTRAINT chk_ppr_planning_session_status
        CHECK (status IN (
            'DRAFT',
            'READY_FOR_SELECTION',
            'SELECTED',
            'PENDING_APPROVAL',
            'APPROVED',
            'REJECTED',
            'CANCELLED'
        )),
    CONSTRAINT fk_ppr_planning_session_department
        FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ppr_planning_session_approved_plan
        FOREIGN KEY (approved_plan_id) REFERENCES ppr_plans(id) ON DELETE RESTRICT
);

CREATE INDEX idx_ppr_planning_sessions_registry
    ON ppr_planning_sessions (planning_year, department_id, status)
    WHERE is_deleted = FALSE;

CREATE TABLE ppr_planning_variants (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    normalized_name VARCHAR(255) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    content_hash VARCHAR(64),
    hash_version INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    inputs_json TEXT,
    task_count INTEGER,
    total_labor_hours NUMERIC(19, 4),
    total_downtime_minutes BIGINT,
    first_planned_date DATE,
    last_planned_date DATE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_ppr_planning_variant_session_name
        UNIQUE (session_id, normalized_name),
    CONSTRAINT chk_ppr_planning_variant_revision
        CHECK (revision >= 0),
    CONSTRAINT chk_ppr_planning_variant_hash_version
        CHECK (hash_version >= 1),
    CONSTRAINT chk_ppr_planning_variant_hash
        CHECK (content_hash IS NULL OR content_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_ppr_planning_variant_status
        CHECK (status IN (
            'DRAFT',
            'CALCULATED',
            'SELECTED',
            'NOT_SELECTED',
            'SUPERSEDED'
        )),
    CONSTRAINT fk_ppr_planning_variant_session
        FOREIGN KEY (session_id) REFERENCES ppr_planning_sessions(id) ON DELETE RESTRICT
);

CREATE INDEX idx_ppr_planning_variants_session
    ON ppr_planning_variants (session_id, created_at)
    WHERE is_deleted = FALSE;

ALTER TABLE ppr_planning_sessions
    ADD CONSTRAINT fk_ppr_planning_session_selected_variant
        FOREIGN KEY (selected_variant_id)
        REFERENCES ppr_planning_variants(id)
        ON DELETE RESTRICT;

CREATE TABLE ppr_planning_variant_items (
    id UUID PRIMARY KEY,
    variant_id UUID NOT NULL,
    revision BIGINT NOT NULL,
    source_item_key VARCHAR(64) NOT NULL,
    source_item_key_version INTEGER NOT NULL,
    equipment_id UUID NOT NULL,
    regulation_id UUID,
    maintenance_rule_id UUID,
    template_id UUID,
    maintenance_type VARCHAR(64),
    trigger_type VARCHAR(64),
    trigger_discriminator VARCHAR(255),
    cycle_ordinal BIGINT,
    planned_date DATE NOT NULL,
    scheduled_start TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    scheduled_end TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    due_date TIMESTAMP WITHOUT TIME ZONE,
    normative_labor_hours NUMERIC(19, 4),
    priority VARCHAR(64),
    department_id UUID,
    equipment_code_snapshot VARCHAR(255) NOT NULL,
    equipment_name_snapshot VARCHAR(255) NOT NULL,
    regulation_name_snapshot VARCHAR(255),
    maintenance_rule_name_snapshot VARCHAR(255),
    template_name_snapshot VARCHAR(255),
    task_title_snapshot VARCHAR(500) NOT NULL,
    work_order_lead_days INTEGER NOT NULL DEFAULT 7,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_ppr_variant_item_revision_source
        UNIQUE (variant_id, revision, source_item_key),
    CONSTRAINT chk_ppr_variant_item_revision
        CHECK (revision >= 1),
    CONSTRAINT chk_ppr_variant_item_source_key_version
        CHECK (source_item_key_version >= 1),
    CONSTRAINT chk_ppr_variant_item_source_key
        CHECK (source_item_key ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_ppr_variant_item_dates
        CHECK (scheduled_start <= scheduled_end),
    CONSTRAINT chk_ppr_variant_item_lead_days
        CHECK (work_order_lead_days BETWEEN 0 AND 365),
    CONSTRAINT fk_ppr_variant_item_variant
        FOREIGN KEY (variant_id) REFERENCES ppr_planning_variants(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ppr_variant_item_equipment
        FOREIGN KEY (equipment_id) REFERENCES equipment(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ppr_variant_item_regulation
        FOREIGN KEY (regulation_id) REFERENCES maintenance_regulations(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ppr_variant_item_rule
        FOREIGN KEY (maintenance_rule_id) REFERENCES equipment_maintenance_rules(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ppr_variant_item_template
        FOREIGN KEY (template_id) REFERENCES maintenance_templates(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ppr_variant_item_department
        FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE RESTRICT
);

CREATE INDEX idx_ppr_variant_items_exact_revision
    ON ppr_planning_variant_items (variant_id, revision, source_item_key);

CREATE TABLE ppr_planning_variant_item_required_evidence (
    variant_item_id UUID NOT NULL,
    evidence_type VARCHAR(32) NOT NULL,
    PRIMARY KEY (variant_item_id, evidence_type),
    CONSTRAINT fk_ppr_variant_item_required_evidence
        FOREIGN KEY (variant_item_id)
        REFERENCES ppr_planning_variant_items(id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_ppr_variant_item_evidence_type
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

CREATE FUNCTION reject_ppr_planning_variant_item_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'ppr_planning_variant_items are immutable';
END;
$$;

CREATE TRIGGER trg_ppr_planning_variant_items_immutable
    BEFORE UPDATE OR DELETE ON ppr_planning_variant_items
    FOR EACH ROW
    EXECUTE FUNCTION reject_ppr_planning_variant_item_mutation();
