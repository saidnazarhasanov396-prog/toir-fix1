ALTER TABLE work_order_tasks
    ADD COLUMN IF NOT EXISTS source_template_id uuid;

ALTER TABLE work_order_tasks
    ADD COLUMN IF NOT EXISTS source_operation_id uuid;

CREATE INDEX IF NOT EXISTS idx_work_order_tasks_source_template
    ON work_order_tasks (source_template_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_work_order_tasks_source_operation
    ON work_order_tasks (source_operation_id)
    WHERE is_deleted = false;
