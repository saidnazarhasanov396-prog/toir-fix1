CREATE INDEX IF NOT EXISTS idx_work_orders_fleet_lifecycle_repairs
    ON work_orders (equipment_id, completed_at, id)
    WHERE is_deleted = false
      AND work_type = 'REPAIR'
      AND status IN ('COMPLETED', 'CLOSED')
      AND completed_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_meter_readings_fleet_lifecycle_latest
    ON meter_readings (meter_id, read_at DESC, id DESC)
    WHERE is_deleted = false;
