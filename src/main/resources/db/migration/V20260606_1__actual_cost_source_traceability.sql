ALTER TABLE actual_costs
    ADD COLUMN IF NOT EXISTS source_type varchar(64),
    ADD COLUMN IF NOT EXISTS source_id uuid;

UPDATE actual_costs
SET source_type = CASE
        WHEN work_order_id IS NOT NULL THEN 'WORK_ORDER'
        WHEN repair_request_id IS NOT NULL THEN 'REPAIR_REQUEST'
        WHEN contractor_work_id IS NOT NULL THEN 'CONTRACTOR_WORK'
        ELSE source_type
    END,
    source_id = CASE
        WHEN work_order_id IS NOT NULL THEN work_order_id
        WHEN repair_request_id IS NOT NULL THEN repair_request_id
        WHEN contractor_work_id IS NOT NULL THEN contractor_work_id
        ELSE source_id
    END
WHERE is_deleted = false
  AND source_type IS NULL;

ALTER TABLE actual_costs
    DROP CONSTRAINT IF EXISTS chk_actual_cost_source_type;

ALTER TABLE actual_costs
    ADD CONSTRAINT chk_actual_cost_source_type
        CHECK (
            source_type IS NULL OR source_type IN (
                'WORK_ORDER',
                'REPAIR_REQUEST',
                'CONTRACTOR_WORK',
                'MATERIAL_ISSUE',
                'LABOR_ENTRY',
                'PROCUREMENT_RECEIPT',
                'WORK_ORDER_MANUAL_WITH_REASON'
            )
        );

CREATE UNIQUE INDEX IF NOT EXISTS ux_actual_cost_source_active
    ON actual_costs (source_type, source_id)
    WHERE is_deleted = false
      AND source_type IS NOT NULL
      AND source_id IS NOT NULL;
