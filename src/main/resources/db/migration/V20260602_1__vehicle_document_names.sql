ALTER TABLE vehicle_documents
    ADD COLUMN IF NOT EXISTS document_name VARCHAR(255);

UPDATE vehicle_documents vd
SET document_name = COALESCE(NULLIF(vd.document_type, ''), uf.original_name, 'Vehicle document')
FROM uploaded_files uf
WHERE vd.file_id = uf.id
  AND (vd.document_name IS NULL OR btrim(vd.document_name) = '');

UPDATE vehicle_documents
SET document_name = 'Vehicle document'
WHERE document_name IS NULL OR btrim(document_name) = '';

ALTER TABLE vehicle_documents
    ALTER COLUMN document_name SET NOT NULL;
