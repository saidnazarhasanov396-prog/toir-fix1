ALTER TABLE work_orders
    ADD COLUMN IF NOT EXISTS repair_act_required boolean NOT NULL DEFAULT false;

ALTER TABLE work_orders
    ADD COLUMN IF NOT EXISTS stoppage_act_required boolean NOT NULL DEFAULT false;

ALTER TABLE work_orders
    ADD COLUMN IF NOT EXISTS repair_act_file_asset_id uuid;

ALTER TABLE work_orders
    ADD COLUMN IF NOT EXISTS stoppage_act_file_asset_id uuid;

CREATE INDEX IF NOT EXISTS idx_work_orders_repair_act_file_asset_id
    ON work_orders(repair_act_file_asset_id)
    WHERE repair_act_file_asset_id IS NOT NULL AND is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_work_orders_stoppage_act_file_asset_id
    ON work_orders(stoppage_act_file_asset_id)
    WHERE stoppage_act_file_asset_id IS NOT NULL AND is_deleted = false;
