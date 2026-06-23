ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS acknowledged_at timestamptz,
    ADD COLUMN IF NOT EXISTS acknowledged_by_id uuid,
    ADD COLUMN IF NOT EXISTS acknowledgement_comment text;

CREATE TABLE IF NOT EXISTS actual_cost_review_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,
    actual_cost_id uuid NOT NULL,
    notification_id uuid,
    route_override_id uuid,
    actor_user_id uuid,
    source varchar(32) NOT NULL,
    event_group varchar(32) NOT NULL,
    event_code varchar(64) NOT NULL,
    title text NOT NULL,
    description text,
    severity varchar(32),
    status varchar(32),
    previous_approval_role_code varchar(128),
    next_approval_role_code varchar(128),
    previous_escalation_role_code varchar(128),
    next_escalation_role_code varchar(128),
    previous_threshold_hours integer,
    next_threshold_hours integer,
    handover_comment text,
    acknowledgement_comment text,
    occurred_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_actual_cost_review_events_actual_cost
    ON actual_cost_review_events (actual_cost_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_actual_cost_review_events_notification
    ON actual_cost_review_events (notification_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_actual_cost_review_events_group
    ON actual_cost_review_events (event_group, event_code)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_actual_cost_review_events_occurred
    ON actual_cost_review_events (occurred_at DESC)
    WHERE is_deleted = false;
