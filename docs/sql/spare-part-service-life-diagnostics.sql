-- Multiple active meters of one type. Designate at most one primary after operator review.
SELECT equipment_id, meter_type, count(*) AS active_meter_count,
       count(*) FILTER (WHERE is_primary) AS active_primary_count,
       array_agg(id ORDER BY updated_at DESC) AS meter_ids
FROM equipment_meters
WHERE is_active = true AND is_deleted = false
GROUP BY equipment_id, meter_type
HAVING count(*) > 1 OR count(*) FILTER (WHERE is_primary) > 1;

-- Vehicle compatibility projections that differ from a unique active primary meter.
SELECT vd.equipment_id, em.id AS meter_id, em.meter_type, em.current_value,
       vd.current_odometer_km, vd.current_engine_hours
FROM vehicle_details vd
JOIN equipment_meters em ON em.equipment_id = vd.equipment_id
WHERE vd.is_deleted = false AND em.is_deleted = false
  AND em.is_active = true AND em.is_primary = true
  AND ((em.meter_type = 'MILEAGE_KM' AND em.current_value <> vd.current_odometer_km)
    OR (em.meter_type = 'ENGINE_HOURS' AND em.current_value <> vd.current_engine_hours));

-- BOM cleanup candidates. These remain BOM rows and are not converted to installations.
SELECT id, equipment_id, spare_part_id, position, criticality
FROM equipment_spare_parts
WHERE is_deleted = false
  AND (position IS NULL OR btrim(position) = ''
    OR criticality IS NULL
    OR upper(btrim(criticality)) NOT IN ('CRITICAL', 'STANDARD', 'OPTIONAL'));

-- Issued material without ledger linkage; do not allocate until reconciled.
SELECT id, work_order_id, spare_part_id, quantity, stock_movement_id
FROM repair_material_usages
WHERE is_deleted = false AND stock_movement_id IS NULL;

-- Historical serial-reuse indicators (the lifecycle unique index prevents new duplicates).
SELECT spare_part_id, lower(serial_number_snapshot) AS normalized_serial,
       count(*) AS usage_count, array_agg(id ORDER BY installed_at) AS installation_ids
FROM spare_part_installations
WHERE is_deleted = false AND serial_number_snapshot IS NOT NULL
GROUP BY spare_part_id, lower(serial_number_snapshot)
HAVING count(*) > 1;
