ALTER TABLE work_orders
    ADD COLUMN IF NOT EXISTS defect_list_id uuid;

ALTER TABLE defect_list_lines
    ADD COLUMN IF NOT EXISTS defect_origin varchar(32) NOT NULL DEFAULT 'UNKNOWN';

CREATE INDEX IF NOT EXISTS idx_work_orders_defect_list_id
    ON work_orders(defect_list_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_defect_list_lines_defect_origin
    ON defect_list_lines(defect_origin)
    WHERE is_deleted = false;
