ALTER TABLE equipment
    ADD COLUMN IF NOT EXISTS category varchar(64) NOT NULL DEFAULT 'PRODUCTION_EQUIPMENT';

UPDATE equipment
SET category = 'PRODUCTION_EQUIPMENT'
WHERE category IS NULL;

ALTER TABLE equipment
    ALTER COLUMN category SET DEFAULT 'PRODUCTION_EQUIPMENT';

ALTER TABLE equipment
    ALTER COLUMN category SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_equipment_category
    ON equipment (category)
    WHERE is_deleted = false;

DO $$
DECLARE
    unique_constraint record;
BEGIN
    FOR unique_constraint IN
        SELECT conrelid::regclass AS table_name, conname
        FROM pg_constraint c
        WHERE c.contype = 'u'
          AND c.conrelid = 'equipment'::regclass
          AND (
              SELECT array_agg(a.attname::text ORDER BY a.attname::text)
              FROM unnest(c.conkey) AS key(attnum)
              JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = key.attnum
          ) IN (ARRAY['code']::text[], ARRAY['inventory_number']::text[])
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT IF EXISTS %I', unique_constraint.table_name, unique_constraint.conname);
    END LOOP;
END;
$$;

CREATE UNIQUE INDEX IF NOT EXISTS idx_equipment_code_active_unique
    ON equipment (code)
    WHERE is_deleted = false;

CREATE UNIQUE INDEX IF NOT EXISTS idx_equipment_inventory_number_active_unique
    ON equipment (inventory_number)
    WHERE is_deleted = false;

CREATE TABLE IF NOT EXISTS vehicle_details (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    equipment_id uuid NOT NULL,
    plate_number varchar(64) NOT NULL,
    vin varchar(128),
    brand varchar(128),
    model varchar(128),
    manufacture_year integer,
    vehicle_type varchar(64) NOT NULL,
    body_number varchar(128),
    chassis_number varchar(128),
    engine_number varchar(128),
    fuel_type varchar(64),
    fuel_tank_capacity double precision,
    carrying_capacity double precision,
    seat_count integer,
    assigned_driver_id uuid,
    current_odometer_km double precision NOT NULL DEFAULT 0,
    current_engine_hours double precision NOT NULL DEFAULT 0,
    registration_certificate_number varchar(128),
    insurance_policy_number varchar(128),
    insurance_expiry_date date,
    technical_inspection_expiry_date date,
    gps_device_id varchar(128),
    CONSTRAINT fk_vehicle_details_equipment
        FOREIGN KEY (equipment_id) REFERENCES equipment(id)
);

UPDATE vehicle_details
SET vin = NULL
WHERE vin IS NOT NULL AND btrim(vin) = '';

DO $$
DECLARE
    unique_constraint record;
BEGIN
    FOR unique_constraint IN
        SELECT conrelid::regclass AS table_name, conname
        FROM pg_constraint c
        WHERE c.contype = 'u'
          AND c.conrelid = 'vehicle_details'::regclass
          AND (
              SELECT array_agg(a.attname::text ORDER BY a.attname::text)
              FROM unnest(c.conkey) AS key(attnum)
              JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = key.attnum
          ) IN (ARRAY['equipment_id']::text[], ARRAY['plate_number']::text[], ARRAY['vin']::text[])
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT IF EXISTS %I', unique_constraint.table_name, unique_constraint.conname);
    END LOOP;
END;
$$;

DROP INDEX IF EXISTS idx_vehicle_details_equipment;
DROP INDEX IF EXISTS idx_vehicle_details_plate;
DROP INDEX IF EXISTS idx_vehicle_details_vin;

CREATE UNIQUE INDEX IF NOT EXISTS idx_vehicle_details_equipment
    ON vehicle_details (equipment_id)
    WHERE is_deleted = false;

CREATE UNIQUE INDEX IF NOT EXISTS idx_vehicle_details_plate
    ON vehicle_details (plate_number)
    WHERE is_deleted = false;

CREATE UNIQUE INDEX IF NOT EXISTS idx_vehicle_details_vin
    ON vehicle_details (vin)
    WHERE is_deleted = false AND vin IS NOT NULL AND btrim(vin) <> '';
