ALTER TABLE technical_documents
    ADD COLUMN IF NOT EXISTS equipment_node_id uuid;

CREATE INDEX IF NOT EXISTS idx_technical_documents_equipment_node_id
    ON technical_documents (equipment_node_id)
    WHERE equipment_node_id IS NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_technical_documents_equipment_node'
          AND conrelid = 'technical_documents'::regclass
    ) THEN
        ALTER TABLE technical_documents
            ADD CONSTRAINT fk_technical_documents_equipment_node
            FOREIGN KEY (equipment_node_id) REFERENCES equipment_nodes (id);
    END IF;
END $$;
