ALTER TABLE counteragents
    ADD COLUMN IF NOT EXISTS inn varchar(9),
    ADD COLUMN IF NOT EXISTS contact_name varchar(255),
    ADD COLUMN IF NOT EXISTS contact_position varchar(255),
    ADD COLUMN IF NOT EXISTS contact_phone varchar(255),
    ADD COLUMN IF NOT EXISTS contact_email varchar(255);

UPDATE counteragents
SET inn = NULLIF(regexp_replace(COALESCE(tax_number, ''), '\D', '', 'g'), '')
WHERE inn IS NULL;

UPDATE counteragents
SET contact_name = contact_person
WHERE contact_name IS NULL;

UPDATE counteragents
SET contact_phone = phone
WHERE contact_phone IS NULL;

UPDATE counteragents
SET contact_email = email
WHERE contact_email IS NULL;

UPDATE counteragents
SET inn = NULL
WHERE inn IS NOT NULL
  AND inn !~ '^[0-9]{9}$';

WITH duplicate_active_inns AS (
    SELECT
        id,
        row_number() OVER (
            PARTITION BY inn
            ORDER BY created_at NULLS LAST, id
        ) AS duplicate_rank
    FROM counteragents
    WHERE is_deleted = false
      AND inn IS NOT NULL
)
UPDATE counteragents c
SET inn = NULL
FROM duplicate_active_inns d
WHERE c.id = d.id
  AND d.duplicate_rank > 1;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_counteragents_inn_9_digits'
          AND conrelid = 'counteragents'::regclass
    ) THEN
        ALTER TABLE counteragents
            ADD CONSTRAINT ck_counteragents_inn_9_digits
            CHECK (inn IS NULL OR inn ~ '^[0-9]{9}$');
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_counteragents_inn_active
    ON counteragents(inn)
    WHERE is_deleted = false AND inn IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_counteragents_inn
    ON counteragents(inn)
    WHERE is_deleted = false;

DROP INDEX IF EXISTS idx_counteragents_tax_number;
DROP INDEX IF EXISTS idx_counteragents_base_inn;

ALTER TABLE counteragents
    DROP COLUMN IF EXISTS tax_number,
    DROP COLUMN IF EXISTS base_inn,
    DROP COLUMN IF EXISTS contact_person,
    DROP COLUMN IF EXISTS phone,
    DROP COLUMN IF EXISTS email,
    DROP COLUMN IF EXISTS specialization;
