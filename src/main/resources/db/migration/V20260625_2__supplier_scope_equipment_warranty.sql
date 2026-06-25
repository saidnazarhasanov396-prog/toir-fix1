ALTER TABLE suppliers
    ADD COLUMN IF NOT EXISTS supplier_type varchar(32) NOT NULL DEFAULT 'BOTH';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_suppliers_supplier_type'
          AND conrelid = 'suppliers'::regclass
    ) THEN
        ALTER TABLE suppliers
            ADD CONSTRAINT ck_suppliers_supplier_type
            CHECK (supplier_type IN ('EQUIPMENT', 'SPARE_PART', 'BOTH'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_suppliers_supplier_type
    ON suppliers (supplier_type)
    WHERE is_deleted = false;

ALTER TABLE equipment
    ADD COLUMN IF NOT EXISTS supplier_id uuid,
    ADD COLUMN IF NOT EXISTS warranty_supplier_id uuid;

CREATE INDEX IF NOT EXISTS idx_equipment_supplier_id
    ON equipment (supplier_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_equipment_warranty_supplier_id
    ON equipment (warranty_supplier_id)
    WHERE is_deleted = false;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_equipment_supplier'
          AND conrelid = 'equipment'::regclass
    ) THEN
        ALTER TABLE equipment
            ADD CONSTRAINT fk_equipment_supplier
            FOREIGN KEY (supplier_id) REFERENCES suppliers(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_equipment_warranty_supplier'
          AND conrelid = 'equipment'::regclass
    ) THEN
        ALTER TABLE equipment
            ADD CONSTRAINT fk_equipment_warranty_supplier
            FOREIGN KEY (warranty_supplier_id) REFERENCES suppliers(id);
    END IF;
END $$;

ALTER TABLE repair_requests
    ADD COLUMN IF NOT EXISTS warranty_supplier_id uuid,
    ADD COLUMN IF NOT EXISTS warranty_supplier_name varchar(255),
    ADD COLUMN IF NOT EXISTS warranty_supplier_contact_person varchar(255),
    ADD COLUMN IF NOT EXISTS warranty_supplier_phone varchar(255),
    ADD COLUMN IF NOT EXISTS warranty_supplier_email varchar(255),
    ADD COLUMN IF NOT EXISTS warranty_start_date_at_creation date,
    ADD COLUMN IF NOT EXISTS warranty_end_date_at_creation date;

CREATE INDEX IF NOT EXISTS idx_repair_requests_warranty_supplier_id
    ON repair_requests (warranty_supplier_id)
    WHERE is_deleted = false;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_repair_requests_warranty_supplier'
          AND conrelid = 'repair_requests'::regclass
    ) THEN
        ALTER TABLE repair_requests
            ADD CONSTRAINT fk_repair_requests_warranty_supplier
            FOREIGN KEY (warranty_supplier_id) REFERENCES suppliers(id);
    END IF;
END $$;
