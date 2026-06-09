CREATE TABLE IF NOT EXISTS work_order_documents (
    id UUID PRIMARY KEY,
    work_order_id UUID NOT NULL REFERENCES work_orders(id),
    file_id UUID NOT NULL REFERENCES uploaded_files(id),
    document_type VARCHAR(64),
    document_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_work_order_documents_work_order_id
    ON work_order_documents (work_order_id);

CREATE INDEX IF NOT EXISTS idx_work_order_documents_file_id
    ON work_order_documents (file_id);

CREATE INDEX IF NOT EXISTS idx_work_order_documents_work_order_document_type
    ON work_order_documents (work_order_id, document_type);
