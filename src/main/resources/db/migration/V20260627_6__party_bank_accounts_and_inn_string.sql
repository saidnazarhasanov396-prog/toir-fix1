CREATE TABLE IF NOT EXISTS contractor_bank_accounts (
    contractor_id uuid NOT NULL REFERENCES contractors (id) ON DELETE CASCADE,
    account_order integer NOT NULL,
    bank_name varchar(255),
    bank_account varchar(64),
    mfo varchar(32),
    PRIMARY KEY (contractor_id, account_order)
);

CREATE TABLE IF NOT EXISTS supplier_bank_accounts (
    supplier_id uuid NOT NULL REFERENCES suppliers (id) ON DELETE CASCADE,
    account_order integer NOT NULL,
    bank_name varchar(255),
    bank_account varchar(64),
    mfo varchar(32),
    PRIMARY KEY (supplier_id, account_order)
);

INSERT INTO contractor_bank_accounts (contractor_id, account_order, bank_name, bank_account, mfo)
SELECT id, 0, bank_name, bank_account, mfo
FROM contractors
WHERE bank_name IS NOT NULL
   OR bank_account IS NOT NULL
   OR mfo IS NOT NULL
ON CONFLICT (contractor_id, account_order) DO NOTHING;

INSERT INTO supplier_bank_accounts (supplier_id, account_order, bank_name, bank_account, mfo)
SELECT id, 0, bank_name, bank_account, mfo
FROM suppliers
WHERE bank_name IS NOT NULL
   OR bank_account IS NOT NULL
   OR mfo IS NOT NULL
ON CONFLICT (supplier_id, account_order) DO NOTHING;

ALTER TABLE contractors
    DROP COLUMN IF EXISTS bank_name,
    DROP COLUMN IF EXISTS bank_account,
    DROP COLUMN IF EXISTS mfo,
    DROP COLUMN IF EXISTS bank_accounts;

ALTER TABLE suppliers
    DROP COLUMN IF EXISTS bank_name,
    DROP COLUMN IF EXISTS bank_account,
    DROP COLUMN IF EXISTS mfo,
    DROP COLUMN IF EXISTS bank_accounts;

ALTER TABLE contractors
    ALTER COLUMN tax_number TYPE varchar(32);

ALTER TABLE suppliers
    ALTER COLUMN tax_number TYPE varchar(32),
    ALTER COLUMN base_inn TYPE varchar(32);
