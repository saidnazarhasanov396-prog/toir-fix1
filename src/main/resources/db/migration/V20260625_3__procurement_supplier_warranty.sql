ALTER TABLE procurement_requests
    ADD COLUMN IF NOT EXISTS supplier_id uuid;

ALTER TABLE procurement_request_lines
    ADD COLUMN IF NOT EXISTS has_warranty boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS warranty_start_date date,
    ADD COLUMN IF NOT EXISTS warranty_end_date date,
    ADD COLUMN IF NOT EXISTS warranty_duration_months integer,
    ADD COLUMN IF NOT EXISTS warranty_supplier_id uuid;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_procurement_requests_supplier'
    ) THEN
        ALTER TABLE procurement_requests
            ADD CONSTRAINT fk_procurement_requests_supplier
            FOREIGN KEY (supplier_id) REFERENCES suppliers(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_procurement_request_lines_warranty_supplier'
    ) THEN
        ALTER TABLE procurement_request_lines
            ADD CONSTRAINT fk_procurement_request_lines_warranty_supplier
            FOREIGN KEY (warranty_supplier_id) REFERENCES suppliers(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_procurement_request_lines_warranty_dates'
    ) THEN
        ALTER TABLE procurement_request_lines
            ADD CONSTRAINT ck_procurement_request_lines_warranty_dates
            CHECK (warranty_start_date IS NULL OR warranty_end_date IS NULL OR warranty_end_date >= warranty_start_date);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_procurement_request_lines_warranty_duration'
    ) THEN
        ALTER TABLE procurement_request_lines
            ADD CONSTRAINT ck_procurement_request_lines_warranty_duration
            CHECK (warranty_duration_months IS NULL OR warranty_duration_months > 0);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_procurement_requests_supplier_id
    ON procurement_requests(supplier_id);

CREATE INDEX IF NOT EXISTS idx_procurement_request_lines_warranty_supplier_id
    ON procurement_request_lines(warranty_supplier_id);
