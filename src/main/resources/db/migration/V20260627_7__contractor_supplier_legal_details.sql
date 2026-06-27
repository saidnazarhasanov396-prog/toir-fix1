ALTER TABLE contractors
    ADD COLUMN IF NOT EXISTS director_name  varchar(255),
    ADD COLUMN IF NOT EXISTS bank_name      varchar(255),
    ADD COLUMN IF NOT EXISTS bank_account   varchar(50),
    ADD COLUMN IF NOT EXISTS mfo            varchar(10);

ALTER TABLE suppliers
    ADD COLUMN IF NOT EXISTS director_name  varchar(255),
    ADD COLUMN IF NOT EXISTS bank_name      varchar(255),
    ADD COLUMN IF NOT EXISTS bank_account   varchar(50),
    ADD COLUMN IF NOT EXISTS mfo            varchar(10);
