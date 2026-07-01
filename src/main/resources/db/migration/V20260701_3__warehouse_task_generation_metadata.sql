ALTER TABLE warehouse_tasks
    ADD COLUMN IF NOT EXISTS generation_key varchar(255),
    ADD COLUMN IF NOT EXISTS auto_generated boolean NOT NULL DEFAULT false;

CREATE UNIQUE INDEX IF NOT EXISTS uq_warehouse_tasks_generation_key
    ON warehouse_tasks (generation_key)
    WHERE generation_key IS NOT NULL AND is_deleted = false;
