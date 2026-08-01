ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS title_uz VARCHAR(255),
    ADD COLUMN IF NOT EXISTS message_uz TEXT,
    ADD COLUMN IF NOT EXISTS title_en VARCHAR(255),
    ADD COLUMN IF NOT EXISTS message_en TEXT;

UPDATE notifications
SET title_uz = COALESCE(title_uz, title),
    message_uz = COALESCE(message_uz, message),
    title_en = COALESCE(title_en, title),
    message_en = COALESCE(message_en, message)
WHERE title_uz IS NULL
   OR message_uz IS NULL
   OR title_en IS NULL
   OR message_en IS NULL;

ALTER TABLE notifications
    ALTER COLUMN title_uz SET NOT NULL,
    ALTER COLUMN message_uz SET NOT NULL,
    ALTER COLUMN title_en SET NOT NULL,
    ALTER COLUMN message_en SET NOT NULL;

ALTER TABLE user_fcm_tokens
    ADD COLUMN IF NOT EXISTS language_code VARCHAR(2) NOT NULL DEFAULT 'ru';
