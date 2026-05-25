CREATE TABLE IF NOT EXISTS vehicle_documents (
    id UUID PRIMARY KEY,
    vehicle_details_id UUID NOT NULL REFERENCES vehicle_details(id),
    file_id UUID NOT NULL REFERENCES uploaded_files(id),
    document_type VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_vehicle_documents_vehicle_details_id
    ON vehicle_documents (vehicle_details_id);

CREATE INDEX IF NOT EXISTS idx_vehicle_documents_file_id
    ON vehicle_documents (file_id);

CREATE INDEX IF NOT EXISTS idx_vehicle_documents_vehicle_document_type
    ON vehicle_documents (vehicle_details_id, document_type);
