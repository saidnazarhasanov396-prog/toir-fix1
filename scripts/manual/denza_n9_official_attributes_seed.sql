-- Denza N9 official technical passport seed
-- Source JSON:
--   src/main/resources/reference-data/denza-n9-real-technical-passport.json
--
-- Important policy:
--   This script intentionally does NOT write to equipment_manual_attributes.
--   It uses only official lifecycle tables:
--   equipment_types, equipment, vehicle_details,
--   equipment_attribute_definitions, equipment_attribute_values.

BEGIN;

DO $$
DECLARE
    v_equipment_type_code constant text := 'DENZA_N9_PHEV';
    v_equipment_type_name constant text := 'Denza N9 Yi San Fang Plug-in Hybrid';
    v_equipment_type_category constant text := 'VEHICLE';
    v_equipment_type_description constant text := 'Official Denza N9 reference equipment type seeded from JSON technical passport.';

    v_equipment_code constant text := 'DENZA_N9_REF';
    v_equipment_inventory_number constant text := 'DENZA-N9-REF';
    v_equipment_name constant text := 'Denza N9 reference vehicle';
    v_equipment_description constant text := 'Reference vehicle row for Denza N9 official attribute values import.';
    v_equipment_manufacturer constant text := 'Denza Automobile Sales Service Co., Ltd.';
    v_equipment_model constant text := 'N9';

    v_vehicle_plate_number constant text := 'REF-DENZA-N9';
    v_vehicle_brand constant text := 'DENZA';
    v_vehicle_model constant text := 'N9';
    v_vehicle_fuel_type constant text := 'Plug-in hybrid gasoline/electric';
    v_vehicle_fuel_tank_capacity constant double precision := 65;

    v_equipment_type_id uuid;
    v_equipment_id uuid;
    v_vehicle_details_id uuid;
    v_definition_id uuid;
    v_value_id uuid;
    v_def record;
    v_attr record;
BEGIN
    -- 1) Create/reuse equipment type.
    SELECT et.id
    INTO v_equipment_type_id
    FROM equipment_types et
    WHERE et.code = v_equipment_type_code
    ORDER BY et.is_deleted ASC, et.updated_at DESC
    LIMIT 1;

    IF v_equipment_type_id IS NULL THEN
        v_equipment_type_id := (
            substring(md5('equipment_type:' || v_equipment_type_code), 1, 8) || '-' ||
            substring(md5('equipment_type:' || v_equipment_type_code), 9, 4) || '-' ||
            substring(md5('equipment_type:' || v_equipment_type_code), 13, 4) || '-' ||
            substring(md5('equipment_type:' || v_equipment_type_code), 17, 4) || '-' ||
            substring(md5('equipment_type:' || v_equipment_type_code), 21, 12)
        )::uuid;

        INSERT INTO equipment_types (
            id, created_at, updated_at, is_deleted,
            code, name, name_en, name_uz, category, description
        )
        VALUES (
            v_equipment_type_id, now(), now(), false,
            v_equipment_type_code,
            v_equipment_type_name,
            v_equipment_type_name,
            v_equipment_type_name,
            v_equipment_type_category,
            v_equipment_type_description
        );
    ELSE
        UPDATE equipment_types
        SET updated_at = now(),
            is_deleted = false,
            name = v_equipment_type_name,
            name_en = v_equipment_type_name,
            name_uz = v_equipment_type_name,
            category = v_equipment_type_category,
            description = v_equipment_type_description
        WHERE id = v_equipment_type_id;
    END IF;

    -- 2) Create/reuse equipment row.
    SELECT e.id
    INTO v_equipment_id
    FROM equipment e
    WHERE e.code = v_equipment_code
       OR e.inventory_number = v_equipment_inventory_number
    ORDER BY
        CASE WHEN e.code = v_equipment_code THEN 0 ELSE 1 END,
        e.is_deleted ASC,
        e.updated_at DESC
    LIMIT 1;

    IF v_equipment_id IS NULL THEN
        v_equipment_id := (
            substring(md5('equipment:' || v_equipment_code), 1, 8) || '-' ||
            substring(md5('equipment:' || v_equipment_code), 9, 4) || '-' ||
            substring(md5('equipment:' || v_equipment_code), 13, 4) || '-' ||
            substring(md5('equipment:' || v_equipment_code), 17, 4) || '-' ||
            substring(md5('equipment:' || v_equipment_code), 21, 12)
        )::uuid;

        INSERT INTO equipment (
            id, created_at, updated_at, is_deleted,
            code, name, inventory_number,
            technical_number, serial_number, model,
            equipment_type_id, department_id, location_id, parent_id, criticality_class_id, responsible_id,
            manufacturer, status, category,
            commissioned_at, warranty_until, description
        )
        VALUES (
            v_equipment_id, now(), now(), false,
            v_equipment_code, v_equipment_name, v_equipment_inventory_number,
            NULL, NULL, v_equipment_model,
            v_equipment_type_id, NULL, NULL, NULL, NULL, NULL,
            v_equipment_manufacturer, 'ACTIVE', 'VEHICLE',
            NULL, NULL, v_equipment_description
        );
    ELSE
        UPDATE equipment e
        SET updated_at = now(),
            is_deleted = false,
            name = v_equipment_name,
            model = v_equipment_model,
            equipment_type_id = v_equipment_type_id,
            manufacturer = v_equipment_manufacturer,
            status = 'ACTIVE',
            category = 'VEHICLE',
            description = v_equipment_description
        WHERE e.id = v_equipment_id;
    END IF;

    -- 3) Create/reuse vehicle_details row and upsert common vehicle fields.
    SELECT vd.id
    INTO v_vehicle_details_id
    FROM vehicle_details vd
    WHERE vd.equipment_id = v_equipment_id
    ORDER BY vd.is_deleted ASC, vd.updated_at DESC
    LIMIT 1;

    IF v_vehicle_details_id IS NULL THEN
        SELECT vd.id
        INTO v_vehicle_details_id
        FROM vehicle_details vd
        WHERE vd.plate_number = v_vehicle_plate_number
        ORDER BY vd.is_deleted ASC, vd.updated_at DESC
        LIMIT 1;
    END IF;

    IF v_vehicle_details_id IS NULL THEN
        v_vehicle_details_id := (
            substring(md5('vehicle_details:' || v_equipment_code), 1, 8) || '-' ||
            substring(md5('vehicle_details:' || v_equipment_code), 9, 4) || '-' ||
            substring(md5('vehicle_details:' || v_equipment_code), 13, 4) || '-' ||
            substring(md5('vehicle_details:' || v_equipment_code), 17, 4) || '-' ||
            substring(md5('vehicle_details:' || v_equipment_code), 21, 12)
        )::uuid;

        INSERT INTO vehicle_details (
            id, created_at, updated_at, is_deleted,
            equipment_id, plate_number, vin,
            brand, model, manufacture_year, vehicle_type,
            body_number, chassis_number, engine_number,
            fuel_type, fuel_tank_capacity, carrying_capacity, seat_count, assigned_driver_id,
            current_odometer_km, current_engine_hours,
            registration_certificate_number, insurance_policy_number,
            insurance_expiry_date, technical_inspection_expiry_date,
            gps_device_id
        )
        VALUES (
            v_vehicle_details_id, now(), now(), false,
            v_equipment_id, v_vehicle_plate_number, NULL,
            v_vehicle_brand, v_vehicle_model, NULL, 'PASSENGER_CAR',
            NULL, NULL, NULL,
            v_vehicle_fuel_type, v_vehicle_fuel_tank_capacity, NULL, NULL, NULL,
            0, 0,
            NULL, NULL,
            NULL, NULL,
            NULL
        );
    ELSE
        UPDATE vehicle_details vd
        SET updated_at = now(),
            is_deleted = false,
            equipment_id = v_equipment_id,
            brand = v_vehicle_brand,
            model = v_vehicle_model,
            vehicle_type = 'PASSENGER_CAR',
            fuel_type = v_vehicle_fuel_type,
            fuel_tank_capacity = v_vehicle_fuel_tank_capacity
        WHERE vd.id = v_vehicle_details_id;
    END IF;

    -- 4) Upsert official attribute definitions.
    FOR v_def IN
        SELECT *
        FROM (
            VALUES
                ('trim', 'Trim', 'TEXT', NULL, 'Identification', 30, false),
                ('market', 'Market', 'TEXT', NULL, 'Identification', 50, false),
                ('specification_source', 'Specification source', 'TEXT', NULL, 'Identification', 90, false),
                ('length_mm', 'Length', 'NUMBER', 'mm', 'Dimensions', 110, false),
                ('width_mm', 'Width', 'NUMBER', 'mm', 'Dimensions', 120, false),
                ('height_mm', 'Height', 'NUMBER', 'mm', 'Dimensions', 130, false),
                ('wheelbase_mm', 'Wheelbase', 'NUMBER', 'mm', 'Dimensions', 140, false),
                ('powertrain_type', 'Powertrain type', 'TEXT', NULL, 'Powertrain', 310, false),
                ('engine_type', 'Engine type', 'TEXT', NULL, 'Powertrain', 320, false),
                ('engine_displacement_l', 'Engine displacement', 'NUMBER', 'L', 'Powertrain', 330, false),
                ('drive_type', 'Drive type', 'TEXT', NULL, 'Powertrain', 370, false),
                ('transmission_type', 'Transmission type', 'TEXT', NULL, 'Powertrain', 380, false),
                ('motor_count', 'Motor count', 'NUMBER', NULL, 'Electric system', 410, false),
                ('front_motor_power_kw', 'Front motor power', 'NUMBER', 'kW', 'Electric system', 420, false),
                ('rear_motor_power_kw', 'Rear motor power', 'TEXT', 'kW', 'Electric system', 430, false),
                ('total_torque_nm', 'Total torque', 'NUMBER', 'Nm', 'Electric system', 460, false),
                ('battery_type', 'Battery type', 'TEXT', NULL, 'Electric system', 470, false),
                ('battery_capacity_kwh', 'Battery capacity', 'NUMBER', 'kWh', 'Electric system', 480, false),
                ('battery_cooling_type', 'Battery cooling type', 'TEXT', NULL, 'Electric system', 500, false),
                ('ac_charging_power_kw', 'AC charging power', 'NUMBER', 'kW', 'Charging', 610, false),
                ('dc_charging_power_kw', 'DC charging power', 'NUMBER', 'kW', 'Charging', 620, false),
                ('dc_charge_time_minutes', 'DC charge time', 'TEXT', 'min', 'Charging', 640, false),
                ('combined_fuel_consumption_l_100km', 'Combined fuel consumption', 'NUMBER', 'L/100km', 'Fuel and range', 730, false),
                ('ev_range_km', 'EV range', 'NUMBER', 'km', 'Fuel and range', 760, false),
                ('combined_range_km', 'Combined range', 'NUMBER', 'km', 'Fuel and range', 770, false),
                ('front_suspension', 'Front suspension', 'TEXT', NULL, 'Chassis', 910, false),
                ('rear_suspension', 'Rear suspension', 'TEXT', NULL, 'Chassis', 920, false),
                ('front_brake_type', 'Front brake type', 'TEXT', NULL, 'Chassis', 930, false),
                ('rear_brake_type', 'Rear brake type', 'TEXT', NULL, 'Chassis', 940, false),
                ('steering_type', 'Steering type', 'TEXT', NULL, 'Chassis', 950, false),
                ('front_tire_size', 'Front tire size', 'TEXT', NULL, 'Tires', 1010, false),
                ('rear_tire_size', 'Rear tire size', 'TEXT', NULL, 'Tires', 1020, false),
                ('acceleration_0_100_sec', '0-100 km/h acceleration', 'NUMBER', 's', 'Performance', 1120, false),
                ('airbags_count', 'Airbags count', 'NUMBER', NULL, 'Safety and ADAS', 1210, false),
                ('adas_level', 'ADAS level', 'TEXT', NULL, 'Safety and ADAS', 1220, false),
                ('lane_assist', 'Lane assist', 'BOOLEAN', NULL, 'Safety and ADAS', 1250, false),
                ('adaptive_cruise', 'Adaptive cruise', 'BOOLEAN', NULL, 'Safety and ADAS', 1260, false),
                ('automatic_emergency_braking', 'Automatic emergency braking', 'BOOLEAN', NULL, 'Safety and ADAS', 1270, false)
        ) AS defs(attribute_key, label, data_type, unit, group_name, sort_order, is_required)
    LOOP
        SELECT d.id
        INTO v_definition_id
        FROM equipment_attribute_definitions d
        WHERE d.equipment_type_id = v_equipment_type_id
          AND d.attribute_key = v_def.attribute_key
        ORDER BY d.is_deleted ASC, d.updated_at DESC
        LIMIT 1;

        IF v_definition_id IS NULL THEN
            v_definition_id := (
                substring(md5('equipment_attribute_definition:' || v_equipment_type_code || ':' || v_def.attribute_key), 1, 8) || '-' ||
                substring(md5('equipment_attribute_definition:' || v_equipment_type_code || ':' || v_def.attribute_key), 9, 4) || '-' ||
                substring(md5('equipment_attribute_definition:' || v_equipment_type_code || ':' || v_def.attribute_key), 13, 4) || '-' ||
                substring(md5('equipment_attribute_definition:' || v_equipment_type_code || ':' || v_def.attribute_key), 17, 4) || '-' ||
                substring(md5('equipment_attribute_definition:' || v_equipment_type_code || ':' || v_def.attribute_key), 21, 12)
            )::uuid;

            INSERT INTO equipment_attribute_definitions (
                id, created_at, updated_at, is_deleted,
                equipment_type_id, option_source_id,
                attribute_key, label, label_ru, label_uz, group_name,
                data_type, unit, options_json, min_value, max_value,
                is_required, sort_order
            )
            VALUES (
                v_definition_id, now(), now(), false,
                v_equipment_type_id, NULL,
                v_def.attribute_key, v_def.label, NULL, NULL, v_def.group_name,
                v_def.data_type, v_def.unit, NULL, NULL, NULL,
                v_def.is_required, v_def.sort_order
            );
        ELSE
            UPDATE equipment_attribute_definitions d
            SET updated_at = now(),
                is_deleted = false,
                equipment_type_id = v_equipment_type_id,
                option_source_id = NULL,
                attribute_key = v_def.attribute_key,
                label = v_def.label,
                label_ru = NULL,
                label_uz = NULL,
                group_name = v_def.group_name,
                data_type = v_def.data_type,
                unit = v_def.unit,
                options_json = NULL,
                min_value = NULL,
                max_value = NULL,
                is_required = v_def.is_required,
                sort_order = v_def.sort_order
            WHERE d.id = v_definition_id;
        END IF;
    END LOOP;

    -- 5) Upsert official attribute values from JSON.
    FOR v_attr IN
        SELECT *
        FROM (
            VALUES
                ('trim', 'Premium / Flagship', NULL, NULL, '["byd_horizon_dealer_specification_sheet"]'),
                ('market', 'Moldova dealer specification with China official product page cross-reference', NULL, NULL, '["byd_horizon_dealer_specification_sheet","denza_official_product_page"]'),
                ('specification_source', 'Denza official product page; BYD Horizon Auto DENZA N9 Plugin Hybrid specification sheet', NULL, NULL, '["denza_official_product_page","byd_horizon_dealer_specification_sheet"]'),
                ('length_mm', NULL, 5258, NULL, '["byd_horizon_dealer_specification_sheet"]'),
                ('width_mm', NULL, 2030, NULL, '["byd_horizon_dealer_specification_sheet"]'),
                ('height_mm', NULL, 1830, NULL, '["byd_horizon_dealer_specification_sheet"]'),
                ('wheelbase_mm', NULL, 3125, NULL, '["byd_horizon_dealer_specification_sheet"]'),
                ('powertrain_type', 'Yi San Fang plug-in hybrid', NULL, NULL, '["denza_official_product_page","byd_horizon_dealer_specification_sheet"]'),
                ('engine_type', '2.0T PHEV turbo engine with direct injection, aluminum engine, Miller cycle, high compression ratio', NULL, NULL, '["byd_horizon_dealer_specification_sheet"]'),
                ('engine_displacement_l', NULL, 2.0, NULL, '["denza_official_product_page","byd_horizon_dealer_specification_sheet"]'),
                ('drive_type', 'Part-time 4WD', NULL, NULL, '["byd_horizon_dealer_specification_sheet"]'),
                ('transmission_type', 'High-efficiency EHS electric hybrid system', NULL, NULL, '["denza_official_product_page"]'),
                ('motor_count', NULL, 3, NULL, '["denza_official_product_page","byd_horizon_dealer_specification_sheet"]'),
                ('front_motor_power_kw', NULL, 200, NULL, '["byd_horizon_dealer_specification_sheet"]'),
                ('rear_motor_power_kw', 'Left rear 240; right rear 240', NULL, NULL, '["byd_horizon_dealer_specification_sheet"]'),
                ('total_torque_nm', NULL, 1035, NULL, '["byd_horizon_dealer_specification_sheet"]'),
                ('battery_type', 'Blade Battery for super hybrid', NULL, NULL, '["byd_horizon_dealer_specification_sheet"]'),
                ('battery_capacity_kwh', NULL, 46.9, NULL, '["byd_horizon_dealer_specification_sheet"]'),
                ('battery_cooling_type', 'Direct cooling battery thermal management system', NULL, NULL, '["byd_horizon_dealer_specification_sheet"]'),
                ('ac_charging_power_kw', NULL, 7, NULL, '["byd_horizon_dealer_specification_sheet"]'),
                ('dc_charging_power_kw', NULL, 100, NULL, '["byd_horizon_dealer_specification_sheet"]'),
                ('dc_charge_time_minutes', '19 (30%-80%)', NULL, NULL, '["byd_horizon_dealer_specification_sheet"]'),
                ('combined_fuel_consumption_l_100km', NULL, 6.3, NULL, '["denza_official_product_page","byd_horizon_dealer_specification_sheet"]'),
                ('ev_range_km', NULL, 202, NULL, '["denza_official_product_page","byd_horizon_dealer_specification_sheet"]'),
                ('combined_range_km', NULL, 1302, NULL, '["denza_official_product_page","byd_horizon_dealer_specification_sheet"]'),
                ('front_suspension', 'Double wishbone independent suspension', NULL, NULL, '["denza_official_product_page","byd_horizon_dealer_specification_sheet"]'),
                ('rear_suspension', 'Five-link independent suspension', NULL, NULL, '["denza_official_product_page","byd_horizon_dealer_specification_sheet"]'),
                ('front_brake_type', 'Perforated ventilated disc; six-piston front caliper standard on Flagship and optional on Premium', NULL, NULL, '["byd_horizon_dealer_specification_sheet"]'),
                ('rear_brake_type', 'Perforated ventilated disc', NULL, NULL, '["byd_horizon_dealer_specification_sheet"]'),
                ('steering_type', 'EPS electric power steering', NULL, NULL, '["byd_horizon_dealer_specification_sheet"]'),
                ('front_tire_size', '275/45 R22', NULL, NULL, '["byd_horizon_dealer_specification_sheet"]'),
                ('rear_tire_size', '275/45 R22', NULL, NULL, '["byd_horizon_dealer_specification_sheet"]'),
                ('acceleration_0_100_sec', NULL, 3.9, NULL, '["denza_official_product_page","byd_horizon_dealer_specification_sheet"]'),
                ('airbags_count', NULL, 9, NULL, '["denza_official_product_page","byd_horizon_dealer_specification_sheet"]'),
                ('adas_level', 'God''s Eye B advanced laser intelligent driving edition (DiPilot 300)', NULL, NULL, '["denza_official_product_page","byd_horizon_dealer_specification_sheet"]'),
                ('lane_assist', NULL, NULL, true, '["byd_horizon_dealer_specification_sheet"]'),
                ('adaptive_cruise', NULL, NULL, true, '["byd_horizon_dealer_specification_sheet"]'),
                ('automatic_emergency_braking', NULL, NULL, true, '["byd_horizon_dealer_specification_sheet"]')
        ) AS attrs(attribute_key, value_text, value_number, value_boolean, source_refs)
    LOOP
        SELECT d.id
        INTO v_definition_id
        FROM equipment_attribute_definitions d
        WHERE d.equipment_type_id = v_equipment_type_id
          AND d.attribute_key = v_attr.attribute_key
        ORDER BY d.is_deleted ASC, d.updated_at DESC
        LIMIT 1;

        IF v_definition_id IS NULL THEN
            RAISE EXCEPTION 'Missing equipment_attribute_definition for key=% and equipment_type_id=%',
                v_attr.attribute_key, v_equipment_type_id;
        END IF;

        SELECT v.id
        INTO v_value_id
        FROM equipment_attribute_values v
        WHERE v.equipment_id = v_equipment_id
          AND v.attribute_definition_id = v_definition_id
        ORDER BY v.is_deleted ASC, v.updated_at DESC
        LIMIT 1;

        IF v_value_id IS NULL THEN
            v_value_id := (
                substring(md5('equipment_attribute_value:' || v_equipment_code || ':' || v_attr.attribute_key), 1, 8) || '-' ||
                substring(md5('equipment_attribute_value:' || v_equipment_code || ':' || v_attr.attribute_key), 9, 4) || '-' ||
                substring(md5('equipment_attribute_value:' || v_equipment_code || ':' || v_attr.attribute_key), 13, 4) || '-' ||
                substring(md5('equipment_attribute_value:' || v_equipment_code || ':' || v_attr.attribute_key), 17, 4) || '-' ||
                substring(md5('equipment_attribute_value:' || v_equipment_code || ':' || v_attr.attribute_key), 21, 12)
            )::uuid;

            INSERT INTO equipment_attribute_values (
                id, created_at, updated_at, is_deleted,
                equipment_id, attribute_definition_id,
                value_text, value_number, value_date, value_boolean, value_option, value_json
            )
            VALUES (
                v_value_id, now(), now(), false,
                v_equipment_id, v_definition_id,
                v_attr.value_text, v_attr.value_number, NULL, v_attr.value_boolean, NULL,
                jsonb_build_object('sourceRefs', (v_attr.source_refs)::jsonb)::text
            );
        ELSE
            UPDATE equipment_attribute_values v
            SET updated_at = now(),
                is_deleted = false,
                equipment_id = v_equipment_id,
                attribute_definition_id = v_definition_id,
                value_text = v_attr.value_text,
                value_number = v_attr.value_number,
                value_date = NULL,
                value_boolean = v_attr.value_boolean,
                value_option = NULL,
                value_json = jsonb_build_object('sourceRefs', (v_attr.source_refs)::jsonb)::text
            WHERE v.id = v_value_id;
        END IF;
    END LOOP;
END $$;

-- Final verification queries.
WITH denza_type AS (
    SELECT *
    FROM equipment_types
    WHERE code = 'DENZA_N9_PHEV'
    ORDER BY is_deleted ASC, updated_at DESC
    LIMIT 1
)
SELECT
    dt.id,
    dt.code,
    dt.name,
    dt.category,
    dt.is_deleted,
    dt.updated_at
FROM denza_type dt;

WITH denza_equipment AS (
    SELECT *
    FROM equipment
    WHERE code = 'DENZA_N9_REF'
       OR inventory_number = 'DENZA-N9-REF'
    ORDER BY CASE WHEN code = 'DENZA_N9_REF' THEN 0 ELSE 1 END, is_deleted ASC, updated_at DESC
    LIMIT 1
)
SELECT
    e.id,
    e.code,
    e.inventory_number,
    e.name,
    e.model,
    e.manufacturer,
    e.status,
    e.category,
    e.equipment_type_id,
    e.is_deleted,
    e.updated_at
FROM denza_equipment e;

WITH denza_equipment AS (
    SELECT id
    FROM equipment
    WHERE code = 'DENZA_N9_REF'
       OR inventory_number = 'DENZA-N9-REF'
    ORDER BY CASE WHEN code = 'DENZA_N9_REF' THEN 0 ELSE 1 END, is_deleted ASC, updated_at DESC
    LIMIT 1
)
SELECT
    vd.id,
    vd.equipment_id,
    vd.plate_number,
    vd.brand,
    vd.model,
    vd.vehicle_type,
    vd.fuel_type,
    vd.fuel_tank_capacity,
    vd.is_deleted,
    vd.updated_at
FROM vehicle_details vd
JOIN denza_equipment e ON e.id = vd.equipment_id
WHERE vd.is_deleted = false;

WITH denza_type AS (
    SELECT id
    FROM equipment_types
    WHERE code = 'DENZA_N9_PHEV'
    ORDER BY is_deleted ASC, updated_at DESC
    LIMIT 1
)
SELECT
    count(*) AS definition_count
FROM equipment_attribute_definitions d
JOIN denza_type dt ON dt.id = d.equipment_type_id
WHERE d.is_deleted = false;

WITH denza_equipment AS (
    SELECT id
    FROM equipment
    WHERE code = 'DENZA_N9_REF'
       OR inventory_number = 'DENZA-N9-REF'
    ORDER BY CASE WHEN code = 'DENZA_N9_REF' THEN 0 ELSE 1 END, is_deleted ASC, updated_at DESC
    LIMIT 1
)
SELECT
    count(*) AS value_count
FROM equipment_attribute_values v
JOIN denza_equipment e ON e.id = v.equipment_id
WHERE v.is_deleted = false;

WITH denza_context AS (
    SELECT
        e.id AS equipment_id,
        et.id AS equipment_type_id
    FROM equipment e
    JOIN equipment_types et ON et.id = e.equipment_type_id
    WHERE et.code = 'DENZA_N9_PHEV'
      AND (e.code = 'DENZA_N9_REF' OR e.inventory_number = 'DENZA-N9-REF')
    ORDER BY CASE WHEN e.code = 'DENZA_N9_REF' THEN 0 ELSE 1 END, e.is_deleted ASC, e.updated_at DESC
    LIMIT 1
)
SELECT
    d.attribute_key,
    d.label,
    d.data_type,
    d.unit,
    v.value_text,
    v.value_number,
    v.value_boolean,
    v.value_json
FROM denza_context ctx
JOIN equipment_attribute_definitions d
    ON d.equipment_type_id = ctx.equipment_type_id
   AND d.is_deleted = false
JOIN equipment_attribute_values v
    ON v.attribute_definition_id = d.id
   AND v.equipment_id = ctx.equipment_id
   AND v.is_deleted = false
ORDER BY d.sort_order ASC, d.attribute_key ASC;

COMMIT;
