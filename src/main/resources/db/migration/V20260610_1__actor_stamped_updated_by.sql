ALTER TABLE work_orders
    ADD COLUMN IF NOT EXISTS updated_by_id uuid;

ALTER TABLE ppr_plans
    ADD COLUMN IF NOT EXISTS updated_by_id uuid;

ALTER TABLE defect_lists
    ADD COLUMN IF NOT EXISTS updated_by_id uuid;

ALTER TABLE stock_movements
    ADD COLUMN IF NOT EXISTS updated_by_id uuid;

ALTER TABLE contractor_works
    ADD COLUMN IF NOT EXISTS updated_by_id uuid;

ALTER TABLE actual_cost_review_route_overrides
    ADD COLUMN IF NOT EXISTS updated_by_id uuid;

ALTER TABLE regulation_change_proposals
    ADD COLUMN IF NOT EXISTS updated_by_id uuid;
