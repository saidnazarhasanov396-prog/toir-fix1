ALTER TABLE ppr_plans
    ADD COLUMN IF NOT EXISTS origin varchar(64);

UPDATE ppr_plans
SET origin = CASE
    WHEN anchor_mode IS NOT NULL THEN 'MAINTENANCE_SCHEDULE'
    ELSE 'MANUAL'
END
WHERE origin IS NULL;

ALTER TABLE ppr_plans
    ALTER COLUMN origin SET DEFAULT 'MANUAL';

ALTER TABLE ppr_plans
    ALTER COLUMN origin SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_ppr_plans_origin_status
    ON ppr_plans (origin, status)
    WHERE is_deleted = false;
