ALTER TABLE work_orders
    ADD COLUMN IF NOT EXISTS work_type varchar(64);

UPDATE work_orders
SET work_type = 'REPAIR'
WHERE work_type IS NULL;

ALTER TABLE work_orders
    ALTER COLUMN work_type SET DEFAULT 'REPAIR';

ALTER TABLE work_orders
    ALTER COLUMN work_type SET NOT NULL;
