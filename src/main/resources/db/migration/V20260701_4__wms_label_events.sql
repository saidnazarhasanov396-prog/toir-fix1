CREATE TABLE IF NOT EXISTS wms_label_events (
    id UUID PRIMARY KEY,
    label_type VARCHAR(32) NOT NULL,
    target_id UUID NOT NULL,
    target_code VARCHAR(128),
    warehouse_id UUID,
    bin_id UUID,
    spare_part_id UUID,
    equipment_id UUID,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_wms_label_events_warehouse
    ON wms_label_events (warehouse_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_wms_label_events_type_created
    ON wms_label_events (label_type, created_at DESC)
    WHERE is_deleted = false;
