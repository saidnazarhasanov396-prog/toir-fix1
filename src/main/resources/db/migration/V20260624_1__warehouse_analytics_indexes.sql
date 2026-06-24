CREATE INDEX IF NOT EXISTS idx_wsl_warehouse_spare_posted
    ON warehouse_stock_ledgers (warehouse_id, spare_part_id, posted_at)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_wrl_warehouse_spare_posted
    ON warehouse_reservation_ledgers (warehouse_id, spare_part_id, posted_at)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_reservations_warehouse_status_updated
    ON reservations (warehouse_id, status, updated_at)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_stock_movements_warehouse_type_date
    ON stock_movements (warehouse_id, type, movement_date)
    WHERE is_deleted = false;
