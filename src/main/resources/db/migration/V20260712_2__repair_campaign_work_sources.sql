CREATE TABLE repair_campaign_work_items (
    id uuid PRIMARY KEY,
    repair_campaign_id uuid NOT NULL,
    source_type varchar(32) NOT NULL,
    source_id uuid,
    equipment_id uuid NOT NULL,
    title varchar(500) NOT NULL,
    status varchar(32) NOT NULL DEFAULT 'PENDING',
    order_number integer NOT NULL,
    notes text,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    CONSTRAINT fk_repair_campaign_work_items_campaign
        FOREIGN KEY (repair_campaign_id) REFERENCES repair_campaigns(id),
    CONSTRAINT fk_repair_campaign_work_items_equipment
        FOREIGN KEY (equipment_id) REFERENCES equipment(id),
    CONSTRAINT chk_repair_campaign_work_items_source_type
        CHECK (source_type IN ('MANUAL', 'DEFECT', 'PPR', 'REPAIR_REQUEST', 'INSPECTION_ROUND', 'WORK_ORDER')),
    CONSTRAINT chk_repair_campaign_work_items_source_identity
        CHECK ((source_type = 'MANUAL' AND source_id IS NULL)
            OR (source_type <> 'MANUAL' AND source_id IS NOT NULL)),
    CONSTRAINT chk_repair_campaign_work_items_status
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'REPLAN_REQUIRED')),
    CONSTRAINT chk_repair_campaign_work_items_order CHECK (order_number >= 0),
    CONSTRAINT uq_repair_campaign_work_items_id_campaign UNIQUE (id, repair_campaign_id)
);

CREATE UNIQUE INDEX uq_repair_campaign_work_items_active_source
    ON repair_campaign_work_items (repair_campaign_id, source_type, source_id)
    WHERE is_deleted = false AND source_id IS NOT NULL;

CREATE UNIQUE INDEX uq_repair_campaign_work_items_active_order
    ON repair_campaign_work_items (repair_campaign_id, order_number)
    WHERE is_deleted = false;

CREATE INDEX idx_repair_campaign_work_items_campaign
    ON repair_campaign_work_items (repair_campaign_id)
    WHERE is_deleted = false;

ALTER TABLE planned_shutdown_work_items
    DROP CONSTRAINT chk_planned_shutdown_work_items_source_type;

ALTER TABLE planned_shutdown_work_items
    ADD CONSTRAINT chk_planned_shutdown_work_items_source_type
    CHECK (source_type IN ('MANUAL', 'DEFECT', 'PPR', 'REPAIR_REQUEST', 'INSPECTION_ROUND',
                           'WORK_ORDER', 'REPAIR_CAMPAIGN'));
