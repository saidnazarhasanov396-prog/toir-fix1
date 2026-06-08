CREATE TABLE IF NOT EXISTS safety_checklist_templates (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    code varchar(255) NOT NULL UNIQUE,
    name varchar(255) NOT NULL,
    work_order_type varchar(64),
    work_type varchar(64),
    active boolean NOT NULL DEFAULT true,
    description text
);

CREATE TABLE IF NOT EXISTS safety_checklist_template_items (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    template_id uuid NOT NULL REFERENCES safety_checklist_templates(id),
    sequence integer NOT NULL,
    label varchar(255) NOT NULL,
    description text,
    category varchar(64) NOT NULL,
    critical boolean NOT NULL DEFAULT false,
    requires_comment boolean NOT NULL DEFAULT false,
    active boolean NOT NULL DEFAULT true
);

CREATE TABLE IF NOT EXISTS work_order_safety_checklists (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    work_order_id uuid NOT NULL REFERENCES work_orders(id),
    template_id uuid REFERENCES safety_checklist_templates(id),
    status varchar(64) NOT NULL,
    checked_by_id uuid,
    checked_at timestamptz,
    remarks text
);

CREATE TABLE IF NOT EXISTS work_order_safety_checklist_items (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    checklist_id uuid NOT NULL REFERENCES work_order_safety_checklists(id),
    template_item_id uuid REFERENCES safety_checklist_template_items(id),
    sequence integer NOT NULL,
    label varchar(255) NOT NULL,
    category varchar(64) NOT NULL,
    critical boolean NOT NULL DEFAULT false,
    requires_comment boolean NOT NULL DEFAULT false,
    status varchar(64) NOT NULL,
    comment text,
    checked_by_id uuid,
    checked_at timestamptz
);

CREATE INDEX IF NOT EXISTS idx_safety_checklist_templates_active_type
    ON safety_checklist_templates(active, work_order_type, work_type)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_work_order_safety_checklists_work_order
    ON work_order_safety_checklists(work_order_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_work_order_safety_checklists_status
    ON work_order_safety_checklists(status)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_work_order_safety_checklist_items_critical_status
    ON work_order_safety_checklist_items(critical, status)
    WHERE is_deleted = false;
