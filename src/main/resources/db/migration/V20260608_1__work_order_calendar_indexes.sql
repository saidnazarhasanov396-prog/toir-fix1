CREATE INDEX IF NOT EXISTS idx_work_orders_planned_date_active
    ON work_orders ((COALESCE(end_planned_at, start_planned_at)))
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_work_orders_department_planned_date_active
    ON work_orders (department_id, (COALESCE(end_planned_at, start_planned_at)))
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_work_orders_equipment_planned_date_active
    ON work_orders (equipment_id, (COALESCE(end_planned_at, start_planned_at)))
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_work_orders_status_planned_date_active
    ON work_orders (status, (COALESCE(end_planned_at, start_planned_at)))
    WHERE is_deleted = false;
