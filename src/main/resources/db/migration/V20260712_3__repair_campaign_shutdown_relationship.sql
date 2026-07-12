CREATE TABLE planned_shutdown_campaigns (
    id uuid PRIMARY KEY,
    planned_shutdown_id uuid NOT NULL,
    repair_campaign_id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    CONSTRAINT fk_planned_shutdown_campaigns_shutdown
        FOREIGN KEY (planned_shutdown_id) REFERENCES planned_shutdowns(id),
    CONSTRAINT fk_planned_shutdown_campaigns_campaign
        FOREIGN KEY (repair_campaign_id) REFERENCES repair_campaigns(id)
);

CREATE UNIQUE INDEX uq_planned_shutdown_campaigns_active_pair
    ON planned_shutdown_campaigns (planned_shutdown_id, repair_campaign_id)
    WHERE is_deleted = false;
CREATE INDEX idx_planned_shutdown_campaigns_campaign
    ON planned_shutdown_campaigns (repair_campaign_id) WHERE is_deleted = false;

CREATE TABLE repair_campaign_work_item_windows (
    id uuid PRIMARY KEY,
    repair_campaign_id uuid NOT NULL,
    repair_campaign_work_item_id uuid NOT NULL,
    planned_shutdown_id uuid NOT NULL,
    shutdown_work_item_id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    CONSTRAINT fk_repair_campaign_work_item_windows_campaign_item
        FOREIGN KEY (repair_campaign_work_item_id, repair_campaign_id)
        REFERENCES repair_campaign_work_items(id, repair_campaign_id),
    CONSTRAINT fk_repair_campaign_work_item_windows_shutdown
        FOREIGN KEY (planned_shutdown_id) REFERENCES planned_shutdowns(id),
    CONSTRAINT fk_repair_campaign_work_item_windows_shutdown_item_owner
        FOREIGN KEY (shutdown_work_item_id, planned_shutdown_id)
        REFERENCES planned_shutdown_work_items(id, planned_shutdown_id)
);

CREATE UNIQUE INDEX uq_repair_campaign_work_item_windows_active_identity
    ON repair_campaign_work_item_windows
        (repair_campaign_work_item_id, planned_shutdown_id, shutdown_work_item_id)
    WHERE is_deleted = false;

CREATE INDEX idx_repair_campaign_work_item_windows_campaign_shutdown
    ON repair_campaign_work_item_windows (repair_campaign_id, planned_shutdown_id)
    WHERE is_deleted = false;
