CREATE TABLE IF NOT EXISTS equipment_documents (
    id UUID PRIMARY KEY,
    equipment_id UUID NOT NULL REFERENCES equipment(id),
    file_id UUID NOT NULL REFERENCES uploaded_files(id),
    document_type VARCHAR(64),
    document_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_equipment_documents_equipment_id
    ON equipment_documents (equipment_id);

CREATE INDEX IF NOT EXISTS idx_equipment_documents_file_id
    ON equipment_documents (file_id);

CREATE INDEX IF NOT EXISTS idx_equipment_documents_equipment_document_type
    ON equipment_documents (equipment_id, document_type);
