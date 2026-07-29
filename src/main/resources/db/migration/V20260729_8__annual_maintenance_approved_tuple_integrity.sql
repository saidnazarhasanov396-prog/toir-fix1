ALTER TABLE ppr_plans
    ADD COLUMN IF NOT EXISTS approved_revision BIGINT,
    ADD COLUMN IF NOT EXISTS approved_content_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS approved_content_hash_version INTEGER;

UPDATE ppr_plans
SET approved_revision = materialized_revision,
    approved_content_hash = calculation_content_hash,
    approved_content_hash_version = calculation_content_hash_version
WHERE materialization_mode = 'APPROVAL_FIRST'
  AND task_materialization_status = 'MATERIALIZED'
  AND approved_revision IS NULL
  AND approved_content_hash IS NULL
  AND approved_content_hash_version IS NULL;

ALTER TABLE ppr_plans
    DROP CONSTRAINT IF EXISTS chk_ppr_plans_approved_tuple,
    DROP CONSTRAINT IF EXISTS chk_ppr_plans_materialized_approved_tuple;

ALTER TABLE ppr_plans
    ADD CONSTRAINT chk_ppr_plans_approved_tuple
        CHECK (
            (
                approved_revision IS NULL
                AND approved_content_hash IS NULL
                AND approved_content_hash_version IS NULL
            )
            OR
            (
                approved_revision IS NOT NULL
                AND approved_content_hash IS NOT NULL
                AND approved_content_hash_version IS NOT NULL
                AND approved_revision >= 1
                AND approved_content_hash ~ '^[0-9a-f]{64}$'
                AND approved_content_hash_version >= 1
            )
        ),
    ADD CONSTRAINT chk_ppr_plans_materialized_approved_tuple
        CHECK (
            task_materialization_status <> 'MATERIALIZED'
            OR (
                materialized_revision IS NOT NULL
                AND approved_revision IS NOT NULL
                AND calculation_revision IS NOT NULL
                AND approved_content_hash IS NOT NULL
                AND calculation_content_hash IS NOT NULL
                AND approved_content_hash_version IS NOT NULL
                AND calculation_content_hash_version IS NOT NULL
                AND materialized_revision = approved_revision
                AND approved_revision = calculation_revision
                AND approved_content_hash = calculation_content_hash
                AND approved_content_hash_version =
                    calculation_content_hash_version
            )
        );
