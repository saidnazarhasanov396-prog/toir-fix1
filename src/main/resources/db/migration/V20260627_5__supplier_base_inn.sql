ALTER TABLE suppliers
    ADD COLUMN IF NOT EXISTS base_inn varchar(255);

UPDATE suppliers
SET base_inn = CASE
    WHEN tax_number ~ '^.+_[0-9]+$'
        THEN regexp_replace(tax_number, '_[0-9]+$', '')
    ELSE tax_number
END
WHERE base_inn IS NULL AND tax_number IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_suppliers_base_inn
    ON suppliers (base_inn)
    WHERE is_deleted = false;
