ALTER TABLE equipment_meters
    ADD COLUMN IF NOT EXISTS is_primary boolean NOT NULL DEFAULT false;

CREATE UNIQUE INDEX IF NOT EXISTS ux_equipment_meters_active_primary
    ON equipment_meters (equipment_id, meter_type)
    WHERE is_primary = true
      AND is_active = true
      AND is_deleted = false;
