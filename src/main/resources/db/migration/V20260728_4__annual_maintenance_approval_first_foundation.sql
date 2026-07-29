-- Expand: approval-first plan state and approval binding metadata.
ALTER TABLE ppr_plans
    ADD COLUMN IF NOT EXISTS materialization_mode VARCHAR(32),
    ADD COLUMN IF NOT EXISTS task_materialization_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS calculation_revision BIGINT,
    ADD COLUMN IF NOT EXISTS calculation_content_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS calculation_content_hash_version INTEGER,
    ADD COLUMN IF NOT EXISTS materialized_revision BIGINT,
    ADD COLUMN IF NOT EXISTS materialized_task_count INTEGER;

ALTER TABLE approval_requests
    ADD COLUMN IF NOT EXISTS calculation_revision BIGINT,
    ADD COLUMN IF NOT EXISTS calculation_content_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS calculation_content_hash_version INTEGER,
    ADD COLUMN IF NOT EXISTS resolved_route_fingerprint VARCHAR(64),
    ADD COLUMN IF NOT EXISTS requester_context_fingerprint VARCHAR(64),
    ADD COLUMN IF NOT EXISTS resolution_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS superseded_by_request_id UUID;

-- Backfill: all existing plans retain their materialized legacy behavior.
UPDATE ppr_plans
SET materialization_mode = 'LEGACY_MATERIALIZED',
    task_materialization_status = 'NOT_APPLICABLE'
WHERE materialization_mode IS NULL
   OR task_materialization_status IS NULL;

-- Defaults: new rows remain legacy-safe until the feature is explicitly enabled.
ALTER TABLE ppr_plans
    ALTER COLUMN materialization_mode SET DEFAULT 'LEGACY_MATERIALIZED',
    ALTER COLUMN task_materialization_status SET DEFAULT 'NOT_APPLICABLE',
    ALTER COLUMN materialization_mode SET NOT NULL,
    ALTER COLUMN task_materialization_status SET NOT NULL;

-- Constraints: plan mode, state, and revision/hash invariants.
ALTER TABLE ppr_plans
    DROP CONSTRAINT IF EXISTS chk_ppr_plans_materialization_mode,
    DROP CONSTRAINT IF EXISTS chk_ppr_plans_task_materialization_status,
    DROP CONSTRAINT IF EXISTS chk_ppr_plans_approval_first_metadata,
    DROP CONSTRAINT IF EXISTS chk_ppr_plans_materialized_metadata,
    DROP CONSTRAINT IF EXISTS chk_ppr_plans_not_materialized_revision,
    DROP CONSTRAINT IF EXISTS chk_ppr_plans_materialized_revision;

ALTER TABLE ppr_plans
    ADD CONSTRAINT chk_ppr_plans_materialization_mode
        CHECK (materialization_mode IN ('LEGACY_MATERIALIZED', 'APPROVAL_FIRST')),
    ADD CONSTRAINT chk_ppr_plans_task_materialization_status
        CHECK (task_materialization_status IN (
            'NOT_APPLICABLE', 'NOT_MATERIALIZED', 'MATERIALIZED'
        )),
    ADD CONSTRAINT chk_ppr_plans_approval_first_metadata
        CHECK (
            materialization_mode <> 'APPROVAL_FIRST'
            OR (
                origin = 'MAINTENANCE_SCHEDULE'
                AND calculation_revision >= 1
                AND calculation_content_hash IS NOT NULL
                AND calculation_content_hash ~ '^[0-9a-f]{64}$'
                AND calculation_content_hash_version >= 1
            )
        ),
    ADD CONSTRAINT chk_ppr_plans_materialized_metadata
        CHECK (
            task_materialization_status <> 'MATERIALIZED'
            OR (
                materialized_revision IS NOT NULL
                AND materialized_task_count IS NOT NULL
                AND materialized_task_count >= 0
            )
        ),
    ADD CONSTRAINT chk_ppr_plans_not_materialized_revision
        CHECK (
            task_materialization_status <> 'NOT_MATERIALIZED'
            OR materialized_revision IS NULL
        ),
    ADD CONSTRAINT chk_ppr_plans_materialized_revision
        CHECK (
            materialized_revision IS NULL
            OR (
                calculation_revision IS NOT NULL
                AND materialized_revision <= calculation_revision
            )
        );

ALTER TABLE ppr_plans
    DROP CONSTRAINT IF EXISTS ppr_plans_status_check;

ALTER TABLE ppr_plans
    ADD CONSTRAINT ppr_plans_status_check
        CHECK (status IN (
            'DRAFT',
            'GENERATED',
            'CALCULATED',
            'APPROVED',
            'IN_PROGRESS',
            'CLOSED',
            'CANCELLED'
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
            'CANCELLED',
            'EXPIRED',
            'FAILED',
            'SUPERSEDED'
        ));

ALTER TABLE approval_requests
    ADD CONSTRAINT chk_approval_requests_resolution_code
        CHECK (
            resolution_code IS NULL
            OR resolution_code IN (
                'USER_CANCELLED',
                'RETURNED_FOR_REWORK',
                'SYSTEM_CANCELLED',
                'TARGET_DELETED',
                'REVIEW_AMEND',
                'STALE_SCOPE',
                'NEW_REVISION',
                'ROUTE_CHANGED'
            )
        );

-- Foreign key and lookup index are added last.
ALTER TABLE approval_requests
    ADD CONSTRAINT fk_approval_requests_superseded_by
        FOREIGN KEY (superseded_by_request_id)
        REFERENCES approval_requests(id)
        ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS idx_approval_requests_superseded_by
    ON approval_requests(superseded_by_request_id)
    WHERE superseded_by_request_id IS NOT NULL;
