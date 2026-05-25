-- Production-safe repair migration:
-- Normalize brigade_members.qualifications for JSON-array semantics used by Hibernate JSON mapping.
-- This migration is additive and leaves existing rows intact except for compatibility normalization.

DO
$$
BEGIN
    IF to_regclass('public.brigade_members') IS NULL THEN
        RETURN;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'brigade_members'
          AND column_name = 'qualifications'
          AND data_type = 'jsonb'
    ) THEN
        UPDATE brigade_members
        SET qualifications =
                CASE
                    WHEN qualifications IS NULL THEN '[]'::jsonb
                    WHEN jsonb_typeof(qualifications) = 'array' THEN qualifications
                    WHEN jsonb_typeof(qualifications) = 'string' AND nullif(qualifications #>> '{}', '') IS NULL THEN '[]'::jsonb
                    WHEN jsonb_typeof(qualifications) = 'string' THEN jsonb_build_array(qualifications #>> '{}')
                    ELSE '[]'::jsonb
                    END
        WHERE qualifications IS NULL
           OR jsonb_typeof(qualifications) <> 'array';

        ALTER TABLE brigade_members
            ALTER COLUMN qualifications SET DEFAULT '[]'::jsonb;

        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conrelid = 'brigade_members'::regclass
              AND conname = 'chk_brigade_members_qualifications_array'
        ) THEN
            ALTER TABLE brigade_members
                ADD CONSTRAINT chk_brigade_members_qualifications_array
                    CHECK (qualifications IS NULL OR jsonb_typeof(qualifications) = 'array');
        END IF;
    END IF;
END
$$;
