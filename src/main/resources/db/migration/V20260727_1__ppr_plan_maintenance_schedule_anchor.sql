ALTER TABLE ppr_plans
    ADD COLUMN IF NOT EXISTS anchor_mode varchar(32);

ALTER TABLE ppr_plans
    DROP CONSTRAINT IF EXISTS ck_ppr_plans_anchor_mode;

ALTER TABLE ppr_plans
    ADD CONSTRAINT ck_ppr_plans_anchor_mode
    CHECK (anchor_mode IS NULL OR anchor_mode IN ('CURRENT', 'RESET_TO_PLAN_START'));
