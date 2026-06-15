CREATE TABLE IF NOT EXISTS user_fcm_tokens (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL,
    token text NOT NULL,
    platform varchar(16) NOT NULL,
    device_id varchar(255),
    active boolean NOT NULL DEFAULT true,
    last_seen_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,
    CONSTRAINT fk_user_fcm_tokens_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT user_fcm_tokens_platform_check CHECK (platform IN ('WEB', 'ANDROID', 'IOS'))
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_user_fcm_tokens_token
    ON user_fcm_tokens (token)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_user_fcm_tokens_user_active
    ON user_fcm_tokens (user_id, active)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_user_fcm_tokens_last_seen
    ON user_fcm_tokens (last_seen_at DESC)
    WHERE is_deleted = false;
