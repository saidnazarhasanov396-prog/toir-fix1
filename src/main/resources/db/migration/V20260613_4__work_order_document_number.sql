ALTER TABLE work_order_documents
    ADD COLUMN IF NOT EXISTS document_number varchar(128);
