CREATE TABLE IF NOT EXISTS uploaded_files (
    id UUID PRIMARY KEY,
    original_name VARCHAR(255) NOT NULL,
    stored_name VARCHAR(255) NOT NULL,
    object_name VARCHAR(512) NOT NULL UNIQUE,
    url VARCHAR(1024),
    content_type VARCHAR(255) NOT NULL,
    extension VARCHAR(32) NOT NULL,
    size_bytes BIGINT NOT NULL,
    uploaded_by UUID NOT NULL,
    category VARCHAR(64) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_uploaded_files_uploaded_by_deleted
    ON uploaded_files (uploaded_by, deleted);

CREATE INDEX IF NOT EXISTS idx_uploaded_files_category_created_at
    ON uploaded_files (category, created_at DESC);
