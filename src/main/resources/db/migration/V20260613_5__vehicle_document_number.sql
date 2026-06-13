ALTER TABLE vehicle_documents
    ADD COLUMN IF NOT EXISTS document_number varchar(128);
