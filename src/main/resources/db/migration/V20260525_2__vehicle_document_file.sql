ALTER TABLE vehicle_details
    ADD COLUMN IF NOT EXISTS document_file_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_vehicle_details_document_file'
    ) THEN
        ALTER TABLE vehicle_details
            ADD CONSTRAINT fk_vehicle_details_document_file
            FOREIGN KEY (document_file_id)
            REFERENCES uploaded_files(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_vehicle_details_document_file
    ON vehicle_details (document_file_id);
