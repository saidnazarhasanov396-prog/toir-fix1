ALTER TABLE ppr_plans
    ADD COLUMN IF NOT EXISTS shift_from_excluded_weekdays boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS maintenance_recurrence_anchor varchar(32) NOT NULL DEFAULT 'REGULATION_DATE';

ALTER TABLE ppr_plans
    DROP CONSTRAINT IF EXISTS chk_ppr_plan_maintenance_recurrence_anchor;

ALTER TABLE ppr_plans
    ADD CONSTRAINT chk_ppr_plan_maintenance_recurrence_anchor
        CHECK (maintenance_recurrence_anchor IN ('REGULATION_DATE', 'SHIFTED_DATE'));

CREATE TABLE IF NOT EXISTS ppr_plan_excluded_weekdays (
    plan_id uuid NOT NULL,
    weekday varchar(16) NOT NULL,
    CONSTRAINT pk_ppr_plan_excluded_weekdays PRIMARY KEY (plan_id, weekday),
    CONSTRAINT fk_ppr_plan_excluded_weekdays_plan
        FOREIGN KEY (plan_id) REFERENCES ppr_plans (id) ON DELETE CASCADE,
    CONSTRAINT chk_ppr_plan_excluded_weekday
        CHECK (weekday IN (
            'MONDAY',
            'TUESDAY',
            'WEDNESDAY',
            'THURSDAY',
            'FRIDAY',
            'SATURDAY',
            'SUNDAY'
        ))
);

CREATE INDEX IF NOT EXISTS idx_ppr_plan_excluded_weekdays_plan
    ON ppr_plan_excluded_weekdays (plan_id);
