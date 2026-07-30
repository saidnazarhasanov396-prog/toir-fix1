ALTER TABLE spare_part_installations
    ADD COLUMN IF NOT EXISTS manual_due_at timestamptz,
    ADD COLUMN IF NOT EXISTS manual_due_by uuid,
    ADD COLUMN IF NOT EXISTS manual_due_reason text;

CREATE INDEX IF NOT EXISTS idx_sp_installations_manual_due
    ON spare_part_installations (manual_due_at)
    WHERE is_deleted = false AND manual_due_at IS NOT NULL;

ALTER TABLE spare_part_installations
    DROP CONSTRAINT IF EXISTS fk_sp_installations_manual_due_by;
ALTER TABLE spare_part_installations
    ADD CONSTRAINT fk_sp_installations_manual_due_by
    FOREIGN KEY (manual_due_by) REFERENCES users(id);

CREATE TABLE IF NOT EXISTS spare_part_due_event_work_orders (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    due_event_id uuid NOT NULL REFERENCES spare_part_due_events(id),
    work_order_id uuid NOT NULL REFERENCES work_orders(id),
    idempotency_key varchar(200) NOT NULL,
    link_status varchar(32) NOT NULL,
    linked_by uuid NOT NULL REFERENCES users(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,
    CONSTRAINT uq_sp_due_wo_idempotency UNIQUE (due_event_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_sp_due_wo_event
    ON spare_part_due_event_work_orders (due_event_id, created_at DESC)
    WHERE is_deleted = false;
CREATE INDEX IF NOT EXISTS idx_sp_due_wo_work_order
    ON spare_part_due_event_work_orders (work_order_id)
    WHERE is_deleted = false;
