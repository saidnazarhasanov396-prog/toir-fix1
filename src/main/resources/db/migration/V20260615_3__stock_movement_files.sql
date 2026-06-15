CREATE TABLE IF NOT EXISTS stock_movement_files (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    stock_movement_id uuid NOT NULL REFERENCES stock_movements (id) ON DELETE CASCADE,
    file_id uuid NOT NULL REFERENCES uploaded_files (id),
    sort_order integer NOT NULL DEFAULT 0,
    created_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT uq_stock_movement_files_file UNIQUE (file_id),
    CONSTRAINT uq_stock_movement_files_sort UNIQUE (stock_movement_id, sort_order)
);

CREATE INDEX IF NOT EXISTS idx_stock_movement_files_movement_id
    ON stock_movement_files (stock_movement_id, sort_order);

CREATE INDEX IF NOT EXISTS idx_stock_movement_files_file_id
    ON stock_movement_files (file_id);
