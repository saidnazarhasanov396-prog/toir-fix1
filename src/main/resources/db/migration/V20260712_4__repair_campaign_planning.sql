ALTER TABLE repair_campaign_work_items
    ADD COLUMN priority varchar(32) NOT NULL DEFAULT 'MEDIUM',
    ADD CONSTRAINT chk_repair_campaign_work_item_priority CHECK (priority IN ('LOW','MEDIUM','HIGH','CRITICAL'));

CREATE TABLE repair_campaign_work_dependencies (
    id uuid PRIMARY KEY,
    repair_campaign_id uuid NOT NULL,
    predecessor_work_item_id uuid NOT NULL,
    successor_work_item_id uuid NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_repair_campaign_dependency_campaign FOREIGN KEY (repair_campaign_id) REFERENCES repair_campaigns(id),
    CONSTRAINT fk_repair_campaign_dependency_predecessor FOREIGN KEY (predecessor_work_item_id, repair_campaign_id)
        REFERENCES repair_campaign_work_items(id, repair_campaign_id),
    CONSTRAINT fk_repair_campaign_dependency_successor FOREIGN KEY (successor_work_item_id, repair_campaign_id)
        REFERENCES repair_campaign_work_items(id, repair_campaign_id),
    CONSTRAINT chk_repair_campaign_dependency_not_self CHECK (predecessor_work_item_id <> successor_work_item_id)
);
CREATE UNIQUE INDEX uq_repair_campaign_dependencies_active_edge
    ON repair_campaign_work_dependencies(repair_campaign_id, predecessor_work_item_id, successor_work_item_id)
    WHERE is_deleted=false;
CREATE INDEX idx_repair_campaign_dependencies_campaign ON repair_campaign_work_dependencies(repair_campaign_id)
    WHERE is_deleted=false;

CREATE TABLE repair_campaign_resource_assignments (
    id uuid PRIMARY KEY,
    repair_campaign_id uuid NOT NULL,
    work_item_id uuid NOT NULL,
    employee_id uuid,
    brigade_id uuid,
    counteragent_id uuid,
    shift_code varchar(64) NOT NULL,
    planned_start_at timestamptz NOT NULL,
    planned_end_at timestamptz NOT NULL,
    competency_requirement varchar(255),
    is_deleted boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_repair_campaign_resource_campaign FOREIGN KEY (repair_campaign_id) REFERENCES repair_campaigns(id),
    CONSTRAINT fk_repair_campaign_resource_work_item FOREIGN KEY (work_item_id, repair_campaign_id)
        REFERENCES repair_campaign_work_items(id, repair_campaign_id),
    CONSTRAINT fk_repair_campaign_resource_employee FOREIGN KEY (employee_id) REFERENCES hr_employees(id),
    CONSTRAINT fk_repair_campaign_resource_brigade FOREIGN KEY (brigade_id) REFERENCES brigades(id),
    CONSTRAINT fk_repair_campaign_resource_counteragent FOREIGN KEY (counteragent_id) REFERENCES counteragents(id),
    CONSTRAINT chk_repair_campaign_resource_exactly_one CHECK (
        num_nonnulls(employee_id, brigade_id, counteragent_id) = 1),
    CONSTRAINT chk_repair_campaign_resource_window CHECK (planned_end_at > planned_start_at),
    CONSTRAINT chk_repair_campaign_resource_shift CHECK (btrim(shift_code) <> '')
);
CREATE UNIQUE INDEX uq_repair_campaign_resources_active_identity
    ON repair_campaign_resource_assignments(repair_campaign_id, work_item_id,
        coalesce(employee_id, '00000000-0000-0000-0000-000000000000'::uuid),
        coalesce(brigade_id, '00000000-0000-0000-0000-000000000000'::uuid),
        coalesce(counteragent_id, '00000000-0000-0000-0000-000000000000'::uuid), planned_start_at, planned_end_at)
    WHERE is_deleted=false;
CREATE INDEX idx_repair_campaign_resources_campaign ON repair_campaign_resource_assignments(repair_campaign_id)
    WHERE is_deleted=false;
