CREATE UNIQUE INDEX IF NOT EXISTS uq_equipment_commissioning_open_equipment
    ON equipment_commissioning_acts (equipment_id)
    WHERE is_deleted = false
      AND status IN ('DRAFT', 'PENDING_APPROVAL');
