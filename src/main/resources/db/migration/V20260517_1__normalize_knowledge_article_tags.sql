DO
$$
BEGIN
    IF to_regclass('public.knowledge_articles') IS NOT NULL THEN
        UPDATE knowledge_articles
        SET tags =
                CASE
                    WHEN tags IS NULL THEN '[]'::jsonb
                    WHEN jsonb_typeof(tags) = 'array' THEN tags
                    WHEN jsonb_typeof(tags) = 'string' AND nullif(tags #>> '{}', '') IS NULL THEN '[]'::jsonb
                    WHEN jsonb_typeof(tags) = 'string' THEN jsonb_build_array(tags #>> '{}')
                    ELSE '[]'::jsonb
                    END
        WHERE tags IS NULL
           OR jsonb_typeof(tags) <> 'array';

        ALTER TABLE knowledge_articles
            ALTER COLUMN tags SET DEFAULT '[]'::jsonb;

        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conrelid = 'knowledge_articles'::regclass
              AND conname = 'chk_knowledge_articles_tags_array'
        ) THEN
            ALTER TABLE knowledge_articles
                ADD CONSTRAINT chk_knowledge_articles_tags_array
                    CHECK (tags IS NULL OR jsonb_typeof(tags) = 'array');
        END IF;
    END IF;
END
$$;
