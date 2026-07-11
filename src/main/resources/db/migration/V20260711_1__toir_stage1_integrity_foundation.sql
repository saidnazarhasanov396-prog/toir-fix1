ALTER TABLE planned_shutdowns
    ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;

ALTER TABLE repair_campaigns
    ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS currency_code varchar(3) NOT NULL DEFAULT 'UZS';

ALTER TABLE work_orders
    ADD COLUMN IF NOT EXISTS requires_shutdown boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS requires_isolation boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS generation_key varchar(512);

ALTER TABLE repair_campaigns
    ALTER COLUMN total_budget TYPE numeric(19,4) USING round(total_budget::numeric, 4),
    ALTER COLUMN total_actual TYPE numeric(19,4) USING round(total_actual::numeric, 4);

ALTER TABLE repair_campaign_stages
    ALTER COLUMN planned_cost TYPE numeric(19,4) USING round(planned_cost::numeric, 4),
    ALTER COLUMN actual_cost TYPE numeric(19,4) USING round(actual_cost::numeric, 4);

ALTER TABLE repair_campaign_departments
    ALTER COLUMN planned_budget TYPE numeric(19,4) USING round(planned_budget::numeric, 4);

CREATE UNIQUE INDEX IF NOT EXISTS uq_work_orders_active_generation_key
    ON work_orders (generation_key)
    WHERE generation_key IS NOT NULL AND is_deleted = false;
