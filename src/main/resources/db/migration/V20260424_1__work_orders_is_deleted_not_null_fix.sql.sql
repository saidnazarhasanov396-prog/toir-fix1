DO
$$
DECLARE
    t text;
BEGIN
    FOR t IN
        SELECT c.table_name
        FROM information_schema.columns c
        WHERE c.table_schema = 'public'
          AND c.column_name = 'is_deleted'
        LOOP
            EXECUTE format('UPDATE %I.%I SET is_deleted = false WHERE is_deleted IS NULL', 'public', t);
            EXECUTE format('ALTER TABLE %I.%I ALTER COLUMN is_deleted SET DEFAULT false', 'public', t);
            EXECUTE format('ALTER TABLE %I.%I ALTER COLUMN is_deleted SET NOT NULL', 'public', t);
        END LOOP;
END
$$;