ALTER TABLE repair_campaigns
    DROP CONSTRAINT IF EXISTS repair_campaigns_status_check,
    DROP CONSTRAINT IF EXISTS repair_campaigns_code_key;

ALTER TABLE repair_campaigns
    ADD COLUMN campaign_type varchar(64),
    ADD COLUMN responsible_employee_id uuid,
    ADD COLUMN priority varchar(32),
    ADD COLUMN objective text,
    ADD COLUMN approval_scope_version bigint,
    ADD COLUMN approval_scope_hash varchar(128),
    ADD COLUMN approved_at timestamptz,
    ADD COLUMN preparation_started_at timestamptz,
    ADD COLUMN started_at timestamptz,
    ADD COLUMN suspended_at timestamptz,
    ADD COLUMN completed_at timestamptz,
    ADD COLUMN closing_started_at timestamptz,
    ADD COLUMN closed_at timestamptz,
    ADD COLUMN cancelled_at timestamptz,
    ADD COLUMN suspended_from_status varchar(32),
    ADD COLUMN closure_version bigint NOT NULL DEFAULT 0;

-- Legacy rows have no current owner, approval snapshot, lifecycle timestamps, or closure evidence.
-- Preserve terminal history, but make every other row explicitly non-startable pending remediation.
UPDATE repair_campaigns
SET status = 'SCOPE_FORMATION',
    notes = CASE
        WHEN notes IS NULL OR btrim(notes) = '' THEN 'LEGACY_REMEDIATION_REQUIRED'
        WHEN notes LIKE '%LEGACY_REMEDIATION_REQUIRED%' THEN notes
        ELSE notes || E'\nLEGACY_REMEDIATION_REQUIRED'
    END
WHERE status NOT IN ('CLOSED', 'CANCELLED');

ALTER TABLE repair_campaigns
    ADD CONSTRAINT fk_repair_campaigns_responsible_employee
        FOREIGN KEY (responsible_employee_id) REFERENCES hr_employees(id),
    ADD CONSTRAINT chk_repair_campaign_status CHECK (status IN (
        'DRAFT', 'SCOPE_FORMATION', 'RESOURCE_CHECK', 'PENDING_APPROVAL', 'APPROVED',
        'PREPARATION', 'IN_PROGRESS', 'SUSPENDED', 'COMPLETED', 'CLOSING', 'CLOSED', 'CANCELLED'
    )),
    ADD CONSTRAINT chk_repair_campaign_priority CHECK (
        priority IS NULL OR priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')
    ),
    ADD CONSTRAINT chk_repair_campaign_dates CHECK (end_date >= start_date),
    ADD CONSTRAINT chk_repair_campaign_approval_scope CHECK (
        (approval_scope_version IS NULL AND approval_scope_hash IS NULL)
        OR (approval_scope_version IS NOT NULL AND approval_scope_hash IS NOT NULL)
    ),
    ADD CONSTRAINT chk_repair_campaign_suspended_source CHECK (
        suspended_from_status IS NULL
        OR suspended_from_status IN ('PREPARATION', 'IN_PROGRESS')
    ),
    ADD CONSTRAINT chk_repair_campaign_closure_version CHECK (closure_version >= 0);

CREATE UNIQUE INDEX uq_repair_campaigns_active_code
    ON repair_campaigns (code)
    WHERE is_deleted = false;

CREATE INDEX idx_repair_campaigns_department_status
    ON repair_campaigns (department_id, status)
    WHERE is_deleted = false;

CREATE TABLE repair_campaign_status_history (
    id uuid PRIMARY KEY,
    repair_campaign_id uuid NOT NULL,
    from_status varchar(32),
    to_status varchar(32) NOT NULL,
    actor_id uuid NOT NULL,
    reason text,
    scope_version bigint,
    window_version bigint,
    correlation_key varchar(255),
    occurred_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_repair_campaign_status_history_campaign
        FOREIGN KEY (repair_campaign_id) REFERENCES repair_campaigns(id),
    CONSTRAINT fk_repair_campaign_status_history_actor
        FOREIGN KEY (actor_id) REFERENCES users(id),
    CONSTRAINT chk_repair_campaign_status_history_from CHECK (
        from_status IS NULL OR from_status IN (
            'DRAFT', 'SCOPE_FORMATION', 'RESOURCE_CHECK', 'PENDING_APPROVAL', 'APPROVED',
            'PREPARATION', 'IN_PROGRESS', 'SUSPENDED', 'COMPLETED', 'CLOSING', 'CLOSED', 'CANCELLED'
        )
    ),
    CONSTRAINT chk_repair_campaign_status_history_to CHECK (to_status IN (
        'DRAFT', 'SCOPE_FORMATION', 'RESOURCE_CHECK', 'PENDING_APPROVAL', 'APPROVED',
        'PREPARATION', 'IN_PROGRESS', 'SUSPENDED', 'COMPLETED', 'CLOSING', 'CLOSED', 'CANCELLED'
    ))
);

CREATE INDEX idx_repair_campaign_status_history_campaign_time
    ON repair_campaign_status_history (repair_campaign_id, occurred_at, id)
    WHERE is_deleted = false;
