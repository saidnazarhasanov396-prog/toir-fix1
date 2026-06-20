CREATE TABLE IF NOT EXISTS equipment_daily_usage (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,

    equipment_id uuid NOT NULL,
    usage_date date NOT NULL,
    usage_value double precision NOT NULL DEFAULT 0,

    CONSTRAINT fk_equipment_daily_usage_equipment
        FOREIGN KEY (equipment_id) REFERENCES equipment(id),
    CONSTRAINT uq_equipment_daily_usage_equipment_date
        UNIQUE (equipment_id, usage_date)
);

CREATE INDEX IF NOT EXISTS idx_equipment_daily_usage_equipment_date
    ON equipment_daily_usage (equipment_id, usage_date)
    WHERE is_deleted = false;

ALTER TABLE equipment
    ADD COLUMN IF NOT EXISTS forecast_consumed_resource double precision,
    ADD COLUMN IF NOT EXISTS forecast_remaining_resource double precision,
    ADD COLUMN IF NOT EXISTS forecast_avg_usage_per_active_day double precision,
    ADD COLUMN IF NOT EXISTS forecast_remaining_active_days bigint,
    ADD COLUMN IF NOT EXISTS forecast_calculated_at date;
