CREATE TABLE atil_equipment_status_outbox (
    id uuid PRIMARY KEY,
    equipment_id uuid NOT NULL,
    atil_vehicle_id uuid NOT NULL,
    history_id uuid NOT NULL UNIQUE,
    idempotency_key varchar(180) NOT NULL UNIQUE,
    payload text NOT NULL,
    status varchar(20) NOT NULL,
    attempts integer NOT NULL DEFAULT 0,
    max_attempts integer NOT NULL DEFAULT 8,
    next_attempt_at timestamptz,
    sent_at timestamptz,
    last_error text,
    is_deleted boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_atil_equipment_status_outbox_ready ON atil_equipment_status_outbox (status, next_attempt_at, created_at);
