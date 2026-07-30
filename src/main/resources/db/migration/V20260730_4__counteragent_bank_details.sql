CREATE TABLE IF NOT EXISTS counteragent_bank_details (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false,
    counteragent_id uuid NOT NULL
        CONSTRAINT fk_counteragent_bank_details_counteragent
            REFERENCES counteragents(id) ON DELETE CASCADE,
    bank_name varchar(255) NOT NULL,
    bank_account varchar(255) NOT NULL,
    mfo varchar(255) NOT NULL,
    is_primary boolean NOT NULL DEFAULT false,
    display_order integer NOT NULL,
    CONSTRAINT ck_counteragent_bank_details_display_order
        CHECK (display_order >= 0)
);

CREATE INDEX IF NOT EXISTS idx_counteragent_bank_details_order
    ON counteragent_bank_details(counteragent_id, display_order, id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_counteragent_bank_details_primary
    ON counteragent_bank_details(counteragent_id)
    WHERE is_primary = true;

INSERT INTO counteragent_bank_details (
    id,
    created_at,
    updated_at,
    is_deleted,
    counteragent_id,
    bank_name,
    bank_account,
    mfo,
    is_primary,
    display_order
)
SELECT
    gen_random_uuid(),
    now(),
    now(),
    false,
    c.id,
    COALESCE(NULLIF(BTRIM(c.bank_name), ''), ''),
    COALESCE(NULLIF(BTRIM(c.bank_account), ''), ''),
    COALESCE(NULLIF(BTRIM(c.mfo), ''), ''),
    true,
    0
FROM counteragents c
WHERE (
        NULLIF(BTRIM(c.bank_name), '') IS NOT NULL
        OR NULLIF(BTRIM(c.bank_account), '') IS NOT NULL
        OR NULLIF(BTRIM(c.mfo), '') IS NOT NULL
    )
  AND NOT EXISTS (
        SELECT 1
        FROM counteragent_bank_details cbd
        WHERE cbd.counteragent_id = c.id
    );
