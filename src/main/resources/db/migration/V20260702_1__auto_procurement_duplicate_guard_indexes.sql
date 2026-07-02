CREATE INDEX IF NOT EXISTS idx_procurement_requests_active_auto_warehouse
    ON procurement_requests (warehouse_id, status)
    WHERE is_deleted = false
      AND source = 'AUTO'
      AND status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'ORDERED', 'PARTIALLY_RECEIVED');

CREATE INDEX IF NOT EXISTS idx_procurement_request_lines_active_spare_part
    ON procurement_request_lines (request_id, spare_part_id)
    WHERE is_deleted = false
      AND spare_part_id IS NOT NULL;
