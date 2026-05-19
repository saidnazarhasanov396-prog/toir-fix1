DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'equipment'
          AND column_name = 'department_id'
          AND is_nullable = 'NO'
    ) THEN
        ALTER TABLE equipment
            ALTER COLUMN department_id DROP NOT NULL;
    END IF;
END $$;
