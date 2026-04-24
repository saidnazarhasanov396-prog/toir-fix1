DO $$
DECLARE
    table_name text;
BEGIN
    FOR table_name IN
        SELECT c.table_name
        FROM information_schema.columns c
        WHERE c.table_schema = 'public'
          AND c.column_name = 'is_deleted'
    LOOP
        EXECUTE format('UPDATE %I.%I SET is_deleted = false WHERE is_deleted IS NULL', 'public', table_name);
        EXECUTE format('ALTER TABLE %I.%I ALTER COLUMN is_deleted SET DEFAULT false', 'public', table_name);
        EXECUTE format('ALTER TABLE %I.%I ALTER COLUMN is_deleted SET NOT NULL', 'public', table_name);
    END LOOP;
END $$;
^;^
