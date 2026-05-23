ALTER TABLE ppr_plans
    ADD COLUMN IF NOT EXISTS start_date date,
    ADD COLUMN IF NOT EXISTS end_date date;

UPDATE ppr_plans
SET start_date = make_date(year, month, 1),
    end_date = (make_date(year, month, 1) + interval '1 month - 1 day')::date
WHERE (start_date IS NULL OR end_date IS NULL)
  AND year IS NOT NULL
  AND month IS NOT NULL;

ALTER TABLE ppr_tasks
    ALTER COLUMN equipment_id DROP NOT NULL;

ALTER TABLE ppr_plans
    ALTER COLUMN start_date SET NOT NULL,
    ALTER COLUMN end_date SET NOT NULL,
    DROP COLUMN IF EXISTS year,
    DROP COLUMN IF EXISTS month;

CREATE INDEX IF NOT EXISTS idx_ppr_plans_date_range
    ON ppr_plans (start_date, end_date)
    WHERE is_deleted = false;
