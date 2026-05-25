ALTER TABLE defects
    ADD COLUMN IF NOT EXISTS equipment_node_id uuid;

CREATE INDEX IF NOT EXISTS idx_defects_equipment_node_id
    ON defects (equipment_node_id)
    WHERE equipment_node_id IS NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_defects_equipment_node'
          AND conrelid = 'defects'::regclass
    ) THEN
        ALTER TABLE defects
            ADD CONSTRAINT fk_defects_equipment_node
            FOREIGN KEY (equipment_node_id) REFERENCES equipment_nodes (id);
    END IF;
END $$;
