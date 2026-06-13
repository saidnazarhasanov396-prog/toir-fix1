CREATE TABLE IF NOT EXISTS equipment_document_files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    equipment_document_id UUID NOT NULL REFERENCES equipment_documents(id) ON DELETE CASCADE,
    file_id UUID NOT NULL REFERENCES uploaded_files(id),
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_equipment_document_files_file UNIQUE (file_id),
    CONSTRAINT uq_equipment_document_files_sort UNIQUE (equipment_document_id, sort_order)
);

CREATE INDEX IF NOT EXISTS idx_equipment_document_files_document_id
    ON equipment_document_files (equipment_document_id, sort_order);

CREATE INDEX IF NOT EXISTS idx_equipment_document_files_file_id
    ON equipment_document_files (file_id);

INSERT INTO equipment_document_files (id, equipment_document_id, file_id, sort_order, created_at)
SELECT gen_random_uuid(), ed.id, ed.file_id, 0, ed.created_at
FROM equipment_documents ed
WHERE ed.file_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM equipment_document_files edf
      WHERE edf.equipment_document_id = ed.id
        AND edf.file_id = ed.file_id
  );
