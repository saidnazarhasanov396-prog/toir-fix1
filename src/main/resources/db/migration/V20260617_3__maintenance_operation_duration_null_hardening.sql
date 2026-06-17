UPDATE maintenance_operations
SET duration_hours = 0
WHERE duration_hours IS NULL;

ALTER TABLE maintenance_operations
    ALTER COLUMN duration_hours SET DEFAULT 0,
    ALTER COLUMN duration_hours SET NOT NULL;
