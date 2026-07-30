ALTER TABLE users
    ADD COLUMN IF NOT EXISTS avatar_file_id uuid;

CREATE INDEX IF NOT EXISTS idx_users_avatar_file_id
    ON users (avatar_file_id)
    WHERE avatar_file_id IS NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_users_avatar_file'
    ) THEN
        ALTER TABLE users
            ADD CONSTRAINT fk_users_avatar_file
            FOREIGN KEY (avatar_file_id)
            REFERENCES uploaded_files(id);
    END IF;
END
$$;
