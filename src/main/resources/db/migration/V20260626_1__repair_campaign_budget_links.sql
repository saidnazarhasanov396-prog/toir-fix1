ALTER TABLE repair_campaigns
    ADD COLUMN IF NOT EXISTS maintenance_budget_id uuid;

ALTER TABLE repair_campaign_stages
    ADD COLUMN IF NOT EXISTS budget_line_id uuid;

ALTER TABLE work_orders
    ADD COLUMN IF NOT EXISTS budget_line_id uuid;

ALTER TABLE repair_campaigns
    ADD CONSTRAINT fk_repair_campaigns_maintenance_budget
        FOREIGN KEY (maintenance_budget_id)
        REFERENCES maintenance_budgets(id);

ALTER TABLE repair_campaign_stages
    ADD CONSTRAINT fk_repair_campaign_stages_budget_line
        FOREIGN KEY (budget_line_id)
        REFERENCES budget_lines(id);

ALTER TABLE work_orders
    ADD CONSTRAINT fk_work_orders_budget_line
        FOREIGN KEY (budget_line_id)
        REFERENCES budget_lines(id);

CREATE INDEX IF NOT EXISTS idx_repair_campaigns_maintenance_budget_id
    ON repair_campaigns(maintenance_budget_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_repair_campaign_stages_budget_line_id
    ON repair_campaign_stages(budget_line_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_work_orders_budget_line_id
    ON work_orders(budget_line_id)
    WHERE is_deleted = false;
