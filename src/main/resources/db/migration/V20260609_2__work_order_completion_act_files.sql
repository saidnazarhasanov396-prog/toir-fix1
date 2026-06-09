ALTER TABLE work_orders
    ADD COLUMN IF NOT EXISTS repair_act_required boolean NOT NULL DEFAULT false;

ALTER TABLE work_orders
    ADD COLUMN IF NOT EXISTS stoppage_act_required boolean NOT NULL DEFAULT false;

ALTER TABLE work_orders
    ADD COLUMN IF NOT EXISTS repair_act_file_asset_id uuid;

ALTER TABLE work_orders
    ADD COLUMN IF NOT EXISTS stoppage_act_file_asset_id uuid;
