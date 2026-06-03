ALTER TABLE maintenance_regulations
    ADD COLUMN IF NOT EXISTS initial_schedule_policy varchar(64) NOT NULL DEFAULT 'FROM_OPERATION_START';

UPDATE maintenance_regulations
SET initial_schedule_policy = 'FROM_OPERATION_START'
WHERE initial_schedule_policy IS NULL;

ALTER TABLE maintenance_regulations
    ALTER COLUMN initial_schedule_policy SET DEFAULT 'FROM_OPERATION_START';

ALTER TABLE maintenance_regulations
    ALTER COLUMN initial_schedule_policy SET NOT NULL;
