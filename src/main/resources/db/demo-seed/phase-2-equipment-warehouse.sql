-- 05. Equipment types.
INSERT INTO equipment_types (id, created_at, updated_at, is_deleted, code, name, name_en, name_uz, category, description)
VALUES
('00000000-0000-0000-0000-00000000f001', now(), now(), false, 'DEMO-PUMP-CENTR', 'Centrifugal pump', 'Centrifugal pump', 'Markazdan qochma nasos', 'Rotating equipment', 'Demo pump type'),
('00000000-0000-0000-0000-00000000f002', now(), now(), false, 'DEMO-COMP-AXIAL', 'Axial compressor', 'Axial compressor', 'Oq kompressor', 'Rotating equipment', 'Demo compressor type'),
('00000000-0000-0000-0000-00000000f003', now(), now(), false, 'DEMO-REACTOR', 'Column reactor', 'Column reactor', 'Kolonna reaktor', 'Static equipment', 'Demo reactor type'),
('00000000-0000-0000-0000-00000000f004', now(), now(), false, 'DEMO-HEATEX', 'Heat exchanger', 'Heat exchanger', 'Issiqlik almashgich', 'Static equipment', 'Demo heat exchanger type')
ON CONFLICT (code) DO NOTHING;

-- 06. Equipment and maintenance regulations.
INSERT INTO equipment (id, created_at, updated_at, is_deleted, code, name, inventory_number, technical_number, serial_number, model, equipment_type_id, department_id, location_id, parent_id, criticality_class_id, responsible_id, manufacturer, status, category, commissioned_at, warranty_until, description)
VALUES
('00000000-0000-0000-0000-000000010001', now(), now(), false, 'DEMO-NAV-AMM-CMP-01', 'Synthesis gas compressor K-1', 'DEMO-INV-1001', 'DEMO-TN-1001', 'DEMO-SN-CMP-001', 'K-500 demo', '00000000-0000-0000-0000-00000000f002', '00000000-0000-0000-0000-00000000d002', '00000000-0000-0000-0000-00000000a102', NULL, '00000000-0000-0000-0000-00000000c601', '00000000-0000-0000-0000-00000000a004', 'Demo Compressor Works', 'ACTIVE', 'PRODUCTION_EQUIPMENT', DATE '2020-02-10', DATE '2027-02-10', 'Critical compressor for ammonia synthesis'),
('00000000-0000-0000-0000-000000010002', now(), now(), false, 'DEMO-NAV-AMM-PMP-01', 'Condensate pump P-101', 'DEMO-INV-1010', 'DEMO-TN-1010', 'DEMO-SN-PMP-001', 'P-250 demo', '00000000-0000-0000-0000-00000000f001', '00000000-0000-0000-0000-00000000d002', '00000000-0000-0000-0000-00000000a102', NULL, '00000000-0000-0000-0000-00000000c601', '00000000-0000-0000-0000-00000000a004', 'Demo Pump Works', 'IN_REPAIR', 'PRODUCTION_EQUIPMENT', DATE '2021-04-15', DATE '2026-04-15', 'Demo pump with active repair workflow'),
('00000000-0000-0000-0000-000000010003', now(), now(), false, 'DEMO-NAV-UREA-RCT-01', 'Urea reactor R-2', 'DEMO-INV-2001', 'DEMO-TN-2001', 'DEMO-SN-RCT-001', 'R-1000 demo', '00000000-0000-0000-0000-00000000f003', '00000000-0000-0000-0000-00000000d003', '00000000-0000-0000-0000-00000000a103', NULL, '00000000-0000-0000-0000-00000000c601', '00000000-0000-0000-0000-00000000a009', 'Demo Reactor Works', 'ACTIVE', 'PRODUCTION_EQUIPMENT', DATE '2019-08-01', DATE '2029-08-01', 'Demo static equipment for inspection and knowledge flows')
ON CONFLICT (id) DO NOTHING;

INSERT INTO maintenance_regulations (id, created_at, updated_at, is_deleted, code, name, description, equipment_type_id, maintenance_kind, normative_labor_hours, is_active, periodicity_unit, periodicity_value, tolerance_days, requires_shutdown, trigger_meter_type, trigger_meter_interval)
VALUES
('00000000-0000-0000-0000-000000020001', now(), now(), false, 'DEMO-REG-PUMP-M', 'Monthly pump maintenance', 'Monthly inspection and lubrication for demo pumps', '00000000-0000-0000-0000-00000000f001', 'PREVENTIVE', 4.0, true, 'MONTH', 1, 5, false, NULL, NULL),
('00000000-0000-0000-0000-000000020002', now(), now(), false, 'DEMO-REG-COMP-Q', 'Quarterly compressor maintenance', 'Quarterly vibration and oil system check', '00000000-0000-0000-0000-00000000f002', 'PREVENTIVE', 16.0, true, 'QUARTER', 1, 10, true, NULL, NULL)
ON CONFLICT (code) DO NOTHING;

-- 07. Warehouses, spare parts, and stock.
INSERT INTO warehouses (id, created_at, updated_at, is_deleted, code, name, department_id, location_id, responsible_id, is_active)
VALUES
('00000000-0000-0000-0000-000000030001', now(), now(), false, 'DEMO-WH-MAIN', 'Demo central warehouse', '00000000-0000-0000-0000-00000000d001', '00000000-0000-0000-0000-00000000a104', '00000000-0000-0000-0000-00000000e005', true),
('00000000-0000-0000-0000-000000030002', now(), now(), false, 'DEMO-WH-AMM', 'Demo ammonia workshop warehouse', '00000000-0000-0000-0000-00000000d002', '00000000-0000-0000-0000-00000000a102', '00000000-0000-0000-0000-00000000e005', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO spare_parts (id, created_at, updated_at, is_deleted, code, name, sku, kind, unit, specification, manufacturer, min_stock)
VALUES
('00000000-0000-0000-0000-000000040001', now(), now(), false, 'DEMO-SP-BRG-6208', 'Bearing 6208', 'DEMO-BRG-6208', 'SPARE_PART', 'pc', '6208 C3', 'Demo Bearing Co', 20),
('00000000-0000-0000-0000-000000040002', now(), now(), false, 'DEMO-SP-SEAL-AMM1', 'Mechanical seal AMM-1', 'DEMO-SEAL-AMM1', 'SPARE_PART', 'pc', 'DN50 ammonia service', 'Demo Seal Co', 10),
('00000000-0000-0000-0000-000000040003', now(), now(), false, 'DEMO-SP-GSK-DN100', 'Gasket DN100', 'DEMO-GSK-DN100', 'CONSUMABLE', 'pc', 'PTFE gasket DN100', 'Demo Gasket Co', 50)
ON CONFLICT (code) DO NOTHING;

INSERT INTO warehouse_stocks (id, created_at, updated_at, is_deleted, warehouse_id, spare_part_id, quantity, reserved_qty, min_qty, max_qty, reorder_point, reorder_qty, avg_daily_usage, bin_location)
VALUES
('00000000-0000-0000-0000-000000050001', now(), now(), false, '00000000-0000-0000-0000-000000030001', '00000000-0000-0000-0000-000000040001', 48, 4, 20, 120, 24, 60, 1.2, 'A-01-01'),
('00000000-0000-0000-0000-000000050002', now(), now(), false, '00000000-0000-0000-0000-000000030001', '00000000-0000-0000-0000-000000040002', 8, 0, 10, 50, 12, 24, 0.4, 'A-02-03'),
('00000000-0000-0000-0000-000000050003', now(), now(), false, '00000000-0000-0000-0000-000000030002', '00000000-0000-0000-0000-000000040003', 140, 10, 50, 300, 60, 120, 2.0, 'B-01-02')
ON CONFLICT (warehouse_id, spare_part_id) DO NOTHING;


-- 17.02. Equipment types, regulations, equipment, warehouse catalog, and PPR volume.
INSERT INTO equipment_types (id, created_at, updated_at, is_deleted, code, name, name_en, name_uz, category, description)
VALUES
('00000000-0000-0000-0000-00000000f005', now(), now(), false, 'DEMO-VALVE-CONTROL', 'Control valve', 'Control valve', 'Rostlovchi klapan', 'Valves and fittings', 'Demo control valve type'),
('00000000-0000-0000-0000-00000000f006', now(), now(), false, 'DEMO-MOTOR-ELECTRIC', 'Electric motor', 'Electric motor', 'Elektr dvigatel', 'Electrical equipment', 'Demo electric motor type'),
('00000000-0000-0000-0000-00000000f007', now(), now(), false, 'DEMO-INSTR-PRESSURE', 'Pressure transmitter', 'Pressure transmitter', 'Bosim datchigi', 'Instrumentation', 'Demo instrumentation type'),
('00000000-0000-0000-0000-00000000f008', now(), now(), false, 'DEMO-VEHICLE-SERVICE', 'Service vehicle', 'Service vehicle', 'Xizmat transporti', 'Fleet', 'Demo service vehicle type')
ON CONFLICT (code) DO NOTHING;

INSERT INTO maintenance_regulations (id, created_at, updated_at, is_deleted, code, name, description, equipment_type_id, maintenance_kind, normative_labor_hours, is_active, periodicity_unit, periodicity_value, tolerance_days, requires_shutdown, trigger_meter_type, trigger_meter_interval)
VALUES
('00000000-0000-0000-0000-000000020003', now(), now(), false, 'DEMO-REG-REACTOR-Y', 'Annual reactor inspection', 'Internal inspection and thickness review', '00000000-0000-0000-0000-00000000f003', 'INSPECTION', 80, true, 'YEAR', 1, 20, true, NULL, NULL),
('00000000-0000-0000-0000-000000020004', now(), now(), false, 'DEMO-REG-HE-Q', 'Quarterly heat exchanger cleaning', 'Cleaning and pressure-drop inspection', '00000000-0000-0000-0000-00000000f004', 'PREVENTIVE', 12, true, 'QUARTER', 1, 7, true, NULL, NULL),
('00000000-0000-0000-0000-000000020005', now(), now(), false, 'DEMO-REG-VALVE-M', 'Monthly control valve inspection', 'Stroke test and leakage check', '00000000-0000-0000-0000-00000000f005', 'INSPECTION', 3, true, 'MONTH', 1, 5, false, NULL, NULL),
('00000000-0000-0000-0000-000000020006', now(), now(), false, 'DEMO-REG-MOTOR-Q', 'Quarterly motor insulation check', 'Insulation resistance and bearing inspection', '00000000-0000-0000-0000-00000000f006', 'ELECTRICAL', 6, true, 'QUARTER', 1, 7, false, NULL, NULL),
('00000000-0000-0000-0000-000000020007', now(), now(), false, 'DEMO-REG-INSTR-M', 'Monthly instrument calibration check', 'Loop check and calibration drift review', '00000000-0000-0000-0000-00000000f007', 'METROLOGICAL', 2, true, 'MONTH', 1, 5, false, NULL, NULL),
('00000000-0000-0000-0000-000000020008', now(), now(), false, 'DEMO-REG-VEHICLE-M', 'Monthly service vehicle inspection', 'Vehicle safety and odometer inspection', '00000000-0000-0000-0000-00000000f008', 'PREVENTIVE', 2, true, 'MONTH', 1, 5, false, NULL, NULL)
ON CONFLICT (code) DO NOTHING;

INSERT INTO equipment (id, created_at, updated_at, is_deleted, code, name, inventory_number, technical_number, serial_number, model, equipment_type_id, department_id, location_id, parent_id, criticality_class_id, responsible_id, manufacturer, status, category, commissioned_at, warranty_until, description)
SELECT ('00000000-0000-0000-0000-' || '00000001' || lpad(gs::text, 4, '0'))::uuid, now(), now(), false,
       'DEMO-' || wk.code || '-' || typ.short_code || '-' || lpad(gs::text, 3, '0'),
       wk.name || ' ' || typ.name || ' ' || lpad(gs::text, 3, '0'),
       'DEMO-INV-' || lpad(gs::text, 4, '0'), 'DEMO-TN-' || lpad(gs::text, 4, '0'), 'DEMO-SN-' || lpad(gs::text, 4, '0'),
       typ.model, typ.type_id, wk.department_id, wk.location_id,
       CASE WHEN gs BETWEEN 4 AND 8 THEN '00000000-0000-0000-0000-000000010001'::uuid WHEN gs BETWEEN 9 AND 12 THEN '00000000-0000-0000-0000-000000010003'::uuid ELSE NULL END,
       '00000000-0000-0000-0000-00000000c601'::uuid,
       (ARRAY['00000000-0000-0000-0000-00000000a004','00000000-0000-0000-0000-00000000a009','00000000-0000-0000-0000-00000000a011'])[1 + ((gs - 1) % 3)]::uuid,
       'Demo Industrial Equipment Works',
       (ARRAY['ACTIVE','ACTIVE','ACTIVE','STANDBY','IN_REPAIR','ACTIVE','CONSERVATION'])[1 + ((gs - 1) % 7)],
       CASE WHEN gs >= 55 THEN 'VEHICLE' ELSE typ.category END,
       DATE '2016-01-01' + (gs * 31), DATE '2028-01-01' + (gs * 17),
       'Production-like demo asset generated for maintenance workflows'
FROM generate_series(4, 60) AS gs
CROSS JOIN LATERAL (
    SELECT (ARRAY['AMM','UREA','HNO3','MECH'])[1 + ((gs - 1) % 4)] AS code,
           (ARRAY['Ammonia','Urea','Nitric acid','Mechanical repair'])[1 + ((gs - 1) % 4)] AS name,
           (ARRAY['00000000-0000-0000-0000-00000000d002','00000000-0000-0000-0000-00000000d003','00000000-0000-0000-0000-00000000d004','00000000-0000-0000-0000-00000000d005'])[1 + ((gs - 1) % 4)]::uuid AS department_id,
           (ARRAY['00000000-0000-0000-0000-00000000a102','00000000-0000-0000-0000-00000000a103','00000000-0000-0000-0000-00000000a109','00000000-0000-0000-0000-00000000a111','00000000-0000-0000-0000-00000000a105','00000000-0000-0000-0000-00000000a107','00000000-0000-0000-0000-00000000a110','00000000-0000-0000-0000-00000000a112'])[1 + ((gs - 1) % 8)]::uuid AS location_id
) AS wk
CROSS JOIN LATERAL (
    SELECT CASE WHEN gs >= 55 THEN 'VEH' ELSE (ARRAY['CMP','PMP','RCT','HEX','VLV','MOT','INS','PMP'])[1 + ((gs - 1) % 8)] END AS short_code,
           CASE WHEN gs >= 55 THEN 'Service vehicle' ELSE (ARRAY['Compressor','Pump','Reactor','Heat exchanger','Control valve','Electric motor','Pressure transmitter','Circulation pump'])[1 + ((gs - 1) % 8)] END AS name,
           CASE WHEN gs >= 55 THEN '00000000-0000-0000-0000-00000000f008' ELSE (ARRAY['00000000-0000-0000-0000-00000000f002','00000000-0000-0000-0000-00000000f001','00000000-0000-0000-0000-00000000f003','00000000-0000-0000-0000-00000000f004','00000000-0000-0000-0000-00000000f005','00000000-0000-0000-0000-00000000f006','00000000-0000-0000-0000-00000000f007','00000000-0000-0000-0000-00000000f001'])[1 + ((gs - 1) % 8)] END::uuid AS type_id,
           CASE WHEN gs >= 55 THEN 'VEH-' || gs ELSE 'MODEL-' || gs END AS model,
           CASE WHEN gs >= 55 THEN 'VEHICLE' ELSE (ARRAY['PRODUCTION_EQUIPMENT','PRODUCTION_EQUIPMENT','PRODUCTION_EQUIPMENT','PRODUCTION_EQUIPMENT','PRODUCTION_EQUIPMENT','ENERGY_EQUIPMENT','INSTRUMENTATION','PRODUCTION_EQUIPMENT'])[1 + ((gs - 1) % 8)] END AS category
) AS typ
ON CONFLICT (id) DO NOTHING;

INSERT INTO warehouses (id, created_at, updated_at, is_deleted, code, name, department_id, location_id, responsible_id, is_active)
VALUES ('00000000-0000-0000-0000-000000030003', now(), now(), false, 'DEMO-WH-MECH', 'Demo mechanical repair warehouse', '00000000-0000-0000-0000-00000000d005', '00000000-0000-0000-0000-00000000a112', '00000000-0000-0000-0000-00000000e005', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO spare_parts (id, created_at, updated_at, is_deleted, code, name, sku, kind, unit, specification, manufacturer, min_stock)
SELECT ('00000000-0000-0000-0000-' || '00000004' || lpad(gs::text, 4, '0'))::uuid, now(), now(), false,
       'DEMO-SP-' || part.code || '-' || lpad(gs::text, 3, '0'), part.name || ' ' || lpad(gs::text, 3, '0'), 'DEMO-SKU-' || lpad(gs::text, 4, '0'),
       part.kind, part.unit, part.spec, part.manufacturer, part.min_stock
FROM generate_series(4, 60) AS gs
CROSS JOIN LATERAL (
    SELECT (ARRAY['BRG','SEAL','GSK','OIL','BELT','BOLT','NUT','FILTER','SENSOR','VALVEKIT'])[1 + ((gs - 1) % 10)] AS code,
           (ARRAY['Bearing','Mechanical seal','Gasket','Industrial oil','V-belt','Bolt set','Nut set','Oil filter','Pressure sensor','Valve repair kit'])[1 + ((gs - 1) % 10)] AS name,
           (ARRAY['SPARE_PART','SPARE_PART','CONSUMABLE','MATERIAL','CONSUMABLE','CONSUMABLE','CONSUMABLE','SPARE_PART','SPARE_PART','SPARE_PART'])[1 + ((gs - 1) % 10)] AS kind,
           (ARRAY['pc','pc','pc','l','pc','set','set','pc','pc','set'])[1 + ((gs - 1) % 10)] AS unit,
           (ARRAY['6200 series','Cartridge seal','DN gasket','ISO VG oil','A-profile belt','M16-M24 bolts','M16-M24 nuts','Hydraulic filter','4-20mA transmitter','Seat/plug/gasket kit'])[1 + ((gs - 1) % 10)] AS spec,
           (ARRAY['Demo Bearing Co','Demo Seal Co','Demo Gasket Co','Demo Oil Co','Demo Belt Co','Demo Fasteners','Demo Fasteners','Demo Filter Co','Demo Instrument Co','Demo Valve Co'])[1 + ((gs - 1) % 10)] AS manufacturer,
           (ARRAY[20,10,50,100,15,200,200,25,8,5])[1 + ((gs - 1) % 10)]::double precision AS min_stock
) AS part
ON CONFLICT (code) DO NOTHING;

INSERT INTO warehouse_stocks (id, created_at, updated_at, is_deleted, warehouse_id, spare_part_id, quantity, reserved_qty, min_qty, max_qty, reorder_point, reorder_qty, avg_daily_usage, bin_location)
SELECT ('00000000-0000-0000-0000-' || '00000005' || lpad(((w.wh_index * 100) + p.part_index)::text, 4, '0'))::uuid, now(), now(), false,
       w.warehouse_id, ('00000000-0000-0000-0000-' || '00000004' || lpad(p.part_index::text, 4, '0'))::uuid,
       CASE WHEN (p.part_index + w.wh_index) % 11 = 0 THEN 3 + (p.part_index % 4) ELSE 30 + (p.part_index * 2) + (w.wh_index * 5) END,
       CASE WHEN p.part_index % 9 = 0 THEN 2 ELSE 0 END,
       CASE WHEN p.part_index % 10 IN (1, 2) THEN 10 ELSE 5 END,
       250, CASE WHEN p.part_index % 10 IN (1, 2) THEN 12 ELSE 8 END, 40, round((0.1 + (p.part_index::numeric / 100)), 2)::double precision,
       chr(64 + w.wh_index) || '-' || lpad(((p.part_index - 1) / 10 + 1)::text, 2, '0') || '-' || lpad(((p.part_index - 1) % 10 + 1)::text, 2, '0')
FROM (VALUES (1, '00000000-0000-0000-0000-000000030001'::uuid), (2, '00000000-0000-0000-0000-000000030002'::uuid), (3, '00000000-0000-0000-0000-000000030003'::uuid)) AS w(wh_index, warehouse_id)
CROSS JOIN generate_series(1, 50) AS p(part_index)
ON CONFLICT (warehouse_id, spare_part_id) DO NOTHING;

INSERT INTO ppr_plans (id, created_at, updated_at, is_deleted, code, name, start_date, end_date, status, department_id, created_by_id, approved_by_id, notes)
VALUES
('00000000-0000-0000-0000-000000060002', now(), now(), false, 'DEMO-PPR-2026-06-UREA', 'Demo June 2026 PPR plan for urea workshop', DATE '2026-06-01', DATE '2026-06-30', 'APPROVED', '00000000-0000-0000-0000-00000000d003', '00000000-0000-0000-0000-00000000a005', '00000000-0000-0000-0000-00000000a002', 'Approved urea monthly PPR plan'),
('00000000-0000-0000-0000-000000060003', now(), now(), false, 'DEMO-PPR-2026-06-HNO3', 'Demo June 2026 PPR plan for nitric acid workshop', DATE '2026-06-01', DATE '2026-06-30', 'IN_PROGRESS', '00000000-0000-0000-0000-00000000d004', '00000000-0000-0000-0000-00000000a005', '00000000-0000-0000-0000-00000000a002', 'In-progress nitric acid PPR plan')
ON CONFLICT (code) DO NOTHING;

INSERT INTO ppr_tasks (id, created_at, updated_at, is_deleted, code, plan_id, regulation_id, equipment_id, title, scheduled_start, scheduled_end, due_date, status, priority, planned_labor_hours, actual_labor_hours, postpone_reason)
SELECT ('00000000-0000-0000-0000-' || '00000006' || lpad((100 + gs)::text, 4, '0'))::uuid, now(), now(), false,
       'DEMO-PPR-TASK-' || lpad(gs::text, 3, '0'),
       ('00000000-0000-0000-0000-' || '00000006000' || (2 + ((gs - 1) % 2))::text)::uuid,
       ('00000000-0000-0000-0000-' || '00000002' || lpad((1 + ((gs - 1) % 8))::text, 4, '0'))::uuid,
       ('00000000-0000-0000-0000-' || '00000001' || lpad((1 + ((gs - 1) % 60))::text, 4, '0'))::uuid,
       'Scheduled maintenance task ' || lpad(gs::text, 3, '0'),
       TIMESTAMP '2026-06-01 08:00:00' + ((gs - 1) || ' days')::interval,
       TIMESTAMP '2026-06-01 12:00:00' + ((gs - 1) || ' days')::interval,
       TIMESTAMP '2026-06-01 16:00:00' + ((gs - 1) || ' days')::interval,
       (ARRAY['PLANNED','APPROVED','IN_PROGRESS','COMPLETED','POSTPONED'])[1 + ((gs - 1) % 5)],
       (ARRAY['LOW','MEDIUM','HIGH','CRITICAL'])[1 + ((gs - 1) % 4)],
       (ARRAY[2.0,4.0,8.0,12.0,16.0])[1 + ((gs - 1) % 5)],
       CASE WHEN gs % 5 = 4 THEN (ARRAY[2.0,4.0,8.0,12.0,16.0])[1 + ((gs - 1) % 5)] ELSE NULL END,
       CASE WHEN gs % 5 = 0 THEN 'Awaiting planned shutdown window' ELSE NULL END
FROM generate_series(3, 75) AS gs
ON CONFLICT (code) DO NOTHING;

-- 17.02.X Equipment and warehouse supplements: hierarchy, passport, dynamic attributes, meters, reservations.
INSERT INTO equipment_types (id, created_at, updated_at, is_deleted, code, name, name_en, name_uz, category, description)
VALUES ('10000000-0000-0000-0000-000000110001', now(), now(), false, 'DEMO-VEHICLE', 'Vehicle equipment', 'Vehicle equipment', 'Transport uskunasi', 'Vehicle', 'Vehicle type for replacement and warehouse transport scenarios')
ON CONFLICT (code) DO NOTHING;

INSERT INTO equipment (id, created_at, updated_at, is_deleted, code, name, inventory_number, technical_number, serial_number, model, equipment_type_id, department_id, location_id, parent_id, criticality_class_id, responsible_id, manufacturer, status, category, commissioned_at, warranty_until, description)
SELECT ('10000000-0000-0000-0000-' || '00000012' || lpad(gs::text, 4, '0'))::uuid,
       now(), now(), false,
       'DEMO-VEH-' || lpad(gs::text, 3, '0'),
       (ARRAY['Forklift','Service truck','Mobile compressor unit','Maintenance van','Field inspection car'])[gs],
       'DEMO-VEH-INV-' || lpad(gs::text, 3, '0'),
       'DEMO-VEH-TN-' || lpad(gs::text, 3, '0'),
       'DEMO-VEH-SN-' || lpad(gs::text, 3, '0'),
       (ARRAY['FG-25','TR-7T','MC-300','VAN-2.5','CAR-1.8'])[gs],
       '10000000-0000-0000-0000-000000110001'::uuid,
       '00000000-0000-0000-0000-00000000d005'::uuid,
       '00000000-0000-0000-0000-00000000a111'::uuid,
       NULL,
       '00000000-0000-0000-0000-00000000c601'::uuid,
       '00000000-0000-0000-0000-00000000a006'::uuid,
       'Navoiy Machinery Plant',
       'ACTIVE',
       'VEHICLE',
       DATE '2023-01-01' + (gs * 30),
       DATE '2028-01-01' + (gs * 30),
       'Transport and field maintenance vehicle'
FROM generate_series(1, 5) AS gs
ON CONFLICT (id) DO NOTHING;

WITH eq AS (
    SELECT id, code, name, row_number() OVER (ORDER BY code) AS rn
    FROM equipment
    WHERE is_deleted = false
      AND code LIKE 'DEMO-%'
)
INSERT INTO equipment_nodes (id, created_at, updated_at, is_deleted, equipment_id, parent_id, code, name, node_type, serial_number, description)
SELECT ('10000000-0000-0000-0000-' || '00000013' || lpad(eq.rn::text, 4, '0'))::uuid,
       now(), now(), false,
       eq.id,
       CASE WHEN eq.rn > 5 THEN ('10000000-0000-0000-0000-' || '00000013000' || (1 + ((eq.rn - 1) % 5))::text)::uuid ELSE NULL END,
       'NODE-' || eq.code,
       eq.name || ' node',
       (ARRAY['ASSEMBLY','UNIT','SUBUNIT','COMPONENT','INSTRUMENT'])[1 + ((eq.rn - 1) % 5)],
       eq.code,
       'Equipment hierarchy node for demo structure'
FROM eq
ON CONFLICT (id) DO NOTHING;

WITH eq AS (
    SELECT id, code, row_number() OVER (ORDER BY code) AS rn
    FROM equipment
    WHERE is_deleted = false
      AND code LIKE 'DEMO-%'
)
INSERT INTO equipment_passports (id, created_at, updated_at, is_deleted, equipment_id, passport_number, manufacturer_serial, factory_number, install_date, last_inspection_date, pressure_bar, throughput, power_kw, voltage_v, notes)
SELECT ('10000000-0000-0000-0000-' || '00000014' || lpad(eq.rn::text, 4, '0'))::uuid,
       now(), now(), false,
       eq.id,
       'PASS-' || eq.code,
       'MS-' || lpad(eq.rn::text, 4, '0'),
       'FN-' || lpad(eq.rn::text, 4, '0'),
       DATE '2020-01-01' + (eq.rn::integer * 13),
       DATE '2026-01-01' + ((eq.rn % 28)::integer),
       4 + (eq.rn % 12),
       10 + (eq.rn * 0.7),
       22 + (eq.rn % 90),
       CASE WHEN eq.rn % 3 = 0 THEN 380 ELSE 220 END,
       'Passport seeded for real-case demo'
FROM eq
ON CONFLICT (id) DO NOTHING;

INSERT INTO vehicle_details (id, created_at, updated_at, is_deleted, equipment_id, plate_number, vehicle_type, model, brand, vin, chassis_number, body_number, engine_number, registration_certificate_number, insurance_policy_number, technical_inspection_expiry_date, insurance_expiry_date, manufacture_year, fuel_type, carrying_capacity, seat_count, fuel_tank_capacity, current_odometer_km, current_engine_hours, assigned_driver_id, gps_device_id)
SELECT ('10000000-0000-0000-0000-' || '00000015' || lpad(gs::text, 4, '0'))::uuid,
       now(), now(), false,
       ('10000000-0000-0000-0000-' || '00000012' || lpad(gs::text, 4, '0'))::uuid,
       '80A' || lpad(gs::text, 3, '0') || 'NA',
       (ARRAY['FORKLIFT','TRUCK','SPECIAL_EQUIPMENT','PASSENGER_CAR','TRUCK'])[gs],
       (ARRAY['FG-25','ISUZU NPR','MC-300','LADA Largus','KAMAZ 65115'])[gs],
       (ARRAY['Toyota','Isuzu','Atlas Copco','Lada','Kamaz'])[gs],
       'VINDEMO' || lpad(gs::text, 11, '0'),
       'CHS' || lpad(gs::text, 8, '0'),
       'BODY' || lpad(gs::text, 8, '0'),
       'ENG' || lpad(gs::text, 8, '0'),
       'REG-' || lpad(gs::text, 6, '0'),
       'INS-' || lpad(gs::text, 6, '0'),
       DATE '2027-01-01' + (gs * 30),
       DATE '2027-06-01' + (gs * 30),
       2019 + gs,
       (ARRAY['DIESEL','DIESEL','DIESEL','PETROL','DIESEL'])[gs],
       1.5 + gs,
       2 + gs,
       55 + (gs * 10),
       12000 + (gs * 3500),
       900 + (gs * 220),
       '00000000-0000-0000-0000-00000000a006'::uuid,
       'GPS-DEMO-' || lpad(gs::text, 3, '0')
FROM generate_series(1, 5) AS gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO equipment_attribute_option_sources (id, created_at, updated_at, is_deleted, code, name, name_ru, name_uz, description)
VALUES
('10000000-0000-0000-0000-000000160001', now(), now(), false, 'ATTR-LUBE-TYPE', 'Lubrication type', 'Тип смазки', 'Moylash turi', 'Available lubrication options'),
('10000000-0000-0000-0000-000000160002', now(), now(), false, 'ATTR-COOLING-MODE', 'Cooling mode', 'Режим охлаждения', 'Sovutish rejimi', 'Cooling mode options')
ON CONFLICT (code) DO NOTHING;

INSERT INTO equipment_attribute_option_items (id, created_at, updated_at, is_deleted, option_source_id, option_id, label, label_ru, label_uz, sort_order, active)
SELECT ('10000000-0000-0000-0000-' || '00000017' || lpad(gs::text, 4, '0'))::uuid,
       now(), now(), false,
       CASE WHEN gs <= 5 THEN '10000000-0000-0000-0000-000000160001'::uuid ELSE '10000000-0000-0000-0000-000000160002'::uuid END,
       (ARRAY['OIL_ISO46','OIL_ISO68','GREASE_EP2','SYNTHETIC','DRY','WATER','AIR','MIXED','OIL_WATER','GLYCOL'])[gs],
       (ARRAY['Oil ISO VG 46','Oil ISO VG 68','Grease EP2','Synthetic oil','Dry running','Water cooling','Air cooling','Mixed cooling','Oil-water cooling','Glycol cooling'])[gs],
       (ARRAY['Масло ISO VG 46','Масло ISO VG 68','Смазка EP2','Синтетическое масло','Сухой ход','Водяное охлаждение','Воздушное охлаждение','Смешанное охлаждение','Масло-водяное охлаждение','Гликолевое охлаждение'])[gs],
       (ARRAY['ISO VG 46 moy','ISO VG 68 moy','EP2 surtma','Sintetik moy','Quruq rejim','Suvli sovutish','Havoli sovutish','Aralash sovutish','Moy-suv sovutish','Glikol sovutish'])[gs],
       gs, true
FROM generate_series(1, 10) AS gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO equipment_attribute_definitions (id, created_at, updated_at, is_deleted, equipment_type_id, option_source_id, attribute_key, label, label_ru, label_uz, group_name, data_type, unit, options_json, min_value, max_value, is_required, sort_order)
VALUES
('10000000-0000-0000-0000-000000180001', now(), now(), false, '00000000-0000-0000-0000-00000000f001', NULL, 'operating_pressure', 'Operating pressure', 'Рабочее давление', 'Ishchi bosim', 'Process', 'NUMBER', 'bar', NULL, 0, 40, true, 1),
('10000000-0000-0000-0000-000000180002', now(), now(), false, '00000000-0000-0000-0000-00000000f001', '10000000-0000-0000-0000-000000160001', 'lubrication_type', 'Lubrication type', 'Тип смазки', 'Moylash turi', 'Maintenance', 'SELECT', NULL, NULL, NULL, NULL, true, 2),
('10000000-0000-0000-0000-000000180003', now(), now(), false, '00000000-0000-0000-0000-00000000f002', NULL, 'vibration_limit', 'Vibration alarm limit', 'Предел вибрации', 'Tebranish limiti', 'Condition', 'NUMBER', 'mm/s', NULL, 1, 15, true, 1),
('10000000-0000-0000-0000-000000180004', now(), now(), false, '00000000-0000-0000-0000-00000000f002', NULL, 'bearing_temp_limit', 'Bearing temperature limit', 'Предел температуры подшипника', 'Podshipnik harorati limiti', 'Condition', 'NUMBER', 'C', NULL, 20, 120, true, 2),
('10000000-0000-0000-0000-000000180005', now(), now(), false, '00000000-0000-0000-0000-00000000f003', '10000000-0000-0000-0000-000000160002', 'cooling_mode', 'Cooling mode', 'Режим охлаждения', 'Sovutish rejimi', 'Process', 'SELECT', NULL, NULL, NULL, NULL, false, 1),
('10000000-0000-0000-0000-000000180006', now(), now(), false, '00000000-0000-0000-0000-00000000f004', NULL, 'heat_transfer_area', 'Heat transfer area', 'Площадь теплообмена', 'Issiqlik almashinuvi maydoni', 'Design', 'NUMBER', 'm2', NULL, 1, 500, true, 1)
ON CONFLICT (id) DO NOTHING;

INSERT INTO equipment_attribute_required_criticality (id, created_at, updated_at, is_deleted, attribute_definition_id, criticality_class_id)
SELECT ('10000000-0000-0000-0000-' || '00000019' || lpad(gs::text, 4, '0'))::uuid,
       now(), now(), false,
       ('10000000-0000-0000-0000-' || '00000018000' || gs::text)::uuid,
       '00000000-0000-0000-0000-00000000c601'::uuid
FROM generate_series(1, 6) AS gs
ON CONFLICT (id) DO NOTHING;

WITH eq AS (
    SELECT id, equipment_type_id, row_number() OVER (ORDER BY code) AS rn
    FROM equipment
    WHERE is_deleted = false
      AND code LIKE 'DEMO-%'
),
defs AS (
    SELECT id, equipment_type_id, attribute_key, data_type, row_number() OVER (ORDER BY id) AS dn
    FROM equipment_attribute_definitions
    WHERE is_deleted = false
)
INSERT INTO equipment_attribute_values (id, created_at, updated_at, is_deleted, equipment_id, attribute_definition_id, value_number, value_text, value_option, value_boolean, value_date, value_json)
SELECT ('10000000-0000-0000-0000-' || '0000001a' || lpad((row_number() OVER (ORDER BY eq.rn, defs.dn))::text, 4, '0'))::uuid,
       now(), now(), false,
       eq.id,
       defs.id,
       CASE WHEN defs.data_type = 'NUMBER' THEN round((3 + (eq.rn::numeric / 3) + defs.dn), 2)::double precision ELSE NULL END,
       CASE WHEN defs.attribute_key = 'heat_transfer_area' THEN 'Shell and tube baseline value' ELSE NULL END,
       CASE WHEN defs.attribute_key = 'lubrication_type' THEN (ARRAY['OIL_ISO46','OIL_ISO68','GREASE_EP2'])[1 + (eq.rn % 3)]
            WHEN defs.attribute_key = 'cooling_mode' THEN (ARRAY['WATER','AIR','MIXED'])[1 + (eq.rn % 3)]
            ELSE NULL END,
       NULL,
       NULL,
       NULL
FROM eq
JOIN defs ON defs.equipment_type_id = eq.equipment_type_id
ON CONFLICT (id) DO NOTHING;

INSERT INTO equipment_attribute_value_history (id, created_at, updated_at, is_deleted, equipment_id, attribute_definition_id, attribute_key, attribute_label, old_value, new_value, changed_by, changed_at, source, reason)
SELECT ('10000000-0000-0000-0000-' || '0000001b' || lpad((row_number() OVER (ORDER BY v.id))::text, 4, '0'))::uuid,
       now(), now(), false,
       v.equipment_id, v.attribute_definition_id,
       d.attribute_key, d.label,
       'baseline',
       COALESCE(v.value_option, v.value_text, COALESCE(v.value_number::text, 'n/a')),
       '00000000-0000-0000-0000-00000000a009'::uuid,
       now() - ((row_number() OVER (ORDER BY v.id) % 20) || ' days')::interval,
       'MANUAL',
       'Initial dynamic passport fill'
FROM equipment_attribute_values v
JOIN equipment_attribute_definitions d ON d.id = v.attribute_definition_id
ON CONFLICT (id) DO NOTHING;

WITH eq AS (
    SELECT id, status, row_number() OVER (ORDER BY code) AS rn
    FROM equipment
    WHERE is_deleted = false
      AND code LIKE 'DEMO-%'
)
INSERT INTO equipment_status_history (id, created_at, updated_at, is_deleted, equipment_id, from_status, to_status, changed_by, changed_at, source, reason)
SELECT ('10000000-0000-0000-0000-' || '0000001c' || lpad(eq.rn::text, 4, '0'))::uuid,
       now(), now(), false,
       eq.id,
       CASE WHEN eq.status = 'ACTIVE' THEN 'STANDBY' ELSE 'ACTIVE' END,
       eq.status,
       '00000000-0000-0000-0000-00000000a003'::uuid,
       now() - ((eq.rn % 25) || ' days')::interval,
       'SYSTEM',
       'Loaded from demo lifecycle history'
FROM eq
ON CONFLICT (id) DO NOTHING;

WITH eq AS (
    SELECT id, code, row_number() OVER (ORDER BY code) AS rn
    FROM equipment
    WHERE is_deleted = false
      AND code LIKE 'DEMO-%'
)
INSERT INTO equipment_meters (id, created_at, updated_at, is_deleted, equipment_id, name, meter_type, unit, current_value, rollover_value, last_read_at, is_active)
SELECT ('10000000-0000-0000-0000-' || '0000001d' || lpad(eq.rn::text, 4, '0'))::uuid,
       now(), now(), false,
       eq.id,
       'Operating counter ' || lpad(eq.rn::text, 3, '0'),
       CASE WHEN eq.code LIKE 'DEMO-VEH-%' THEN 'MILEAGE_KM' ELSE 'CYCLES' END,
       CASE WHEN eq.code LIKE 'DEMO-VEH-%' THEN 'km' ELSE 'cycles' END,
       1000 + (eq.rn * 125),
       NULL,
       now() - ((eq.rn % 5) || ' hours')::interval,
       true
FROM eq
ON CONFLICT (id) DO NOTHING;

INSERT INTO meter_readings (id, created_at, updated_at, is_deleted, meter_id, equipment_id, source, value, delta, read_at, recorded_by_user_id, device_id, note)
SELECT ('10000000-0000-0000-0000-' || '0000001e' || lpad((row_number() OVER (ORDER BY m.id, gs))::text, 4, '0'))::uuid,
       now(), now(), false,
       m.id,
       m.equipment_id,
       (ARRAY['MANUAL','SCADA','IOT'])[1 + ((gs - 1) % 3)],
       m.current_value - ((4 - gs) * 10),
       10,
       now() - ((4 - gs) || ' days')::interval,
       '00000000-0000-0000-0000-00000000a009'::uuid,
       'DEV-' || lpad(gs::text, 3, '0'),
       'Demo periodic meter reading'
FROM equipment_meters m
CROSS JOIN generate_series(1, 4) AS gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO condition_readings (id, created_at, updated_at, is_deleted, equipment_id, parameter, value, unit, warn_high, alarm_high, warn_low, alarm_low, severity, recorded_at, recorded_by, notes)
SELECT ('10000000-0000-0000-0000-' || '0000001f' || lpad(gs::text, 4, '0'))::uuid,
       now(), now(), false,
       ('00000000-0000-0000-0000-' || '00000001' || lpad((1 + ((gs - 1) % 60))::text, 4, '0'))::uuid,
       (ARRAY['VIBRATION','TEMPERATURE','PRESSURE','FLOW_RATE'])[1 + ((gs - 1) % 4)],
       (ARRAY[3.2,78.5,8.4,12.2])[1 + ((gs - 1) % 4)] + (gs % 3),
       (ARRAY['mm/s','C','bar','m3/h'])[1 + ((gs - 1) % 4)],
       (ARRAY[4.5,80.0,10.0,15.0])[1 + ((gs - 1) % 4)],
       (ARRAY[7.1,95.0,14.0,20.0])[1 + ((gs - 1) % 4)],
       NULL, NULL,
       CASE WHEN gs % 9 = 0 THEN 'ALARM' WHEN gs % 4 = 0 THEN 'WARN' ELSE 'OK' END,
       now() - ((gs % 14) || ' hours')::interval,
       '00000000-0000-0000-0000-00000000a009'::uuid,
       'Demo condition monitoring trend'
FROM generate_series(1, 40) AS gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO calibration_records (id, created_at, updated_at, is_deleted, equipment_id, certificate_number, performed_by, performed_at, next_due_at, result, tolerance, measured_error, unit, notes)
SELECT ('10000000-0000-0000-0000-' || '00000020' || lpad(gs::text, 4, '0'))::uuid,
       now(), now(), false,
       ('00000000-0000-0000-0000-' || '00000001' || lpad((1 + ((gs - 1) % 60))::text, 4, '0'))::uuid,
       'CAL-DEMO-' || lpad(gs::text, 4, '0'),
       'Demo Metrology Center',
       DATE '2025-01-01' + (gs * 11),
       DATE '2026-12-01' + (gs * 9),
       'PASS',
       0.5,
       0.1 + (gs::double precision / 100),
       '%',
       'Calibration seeded for demo traceability'
FROM generate_series(1, 24) AS gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO materials (id, created_at, updated_at, is_deleted, code, name, kind, unit, min_stock, specification)
SELECT ('10000000-0000-0000-0000-' || '00000021' || lpad(gs::text, 4, '0'))::uuid,
       now(), now(), false,
       'MAT-DEMO-' || lpad(gs::text, 3, '0'),
       (ARRAY['Motor oil ISO VG 46','Cleaning solvent','Welding electrode','Anti-corrosion coating','Bearing grease EP2'])[1 + ((gs - 1) % 5)] || ' #' || lpad(gs::text, 3, '0'),
       (ARRAY['MATERIAL','MATERIAL','CONSUMABLE','MATERIAL','MATERIAL'])[1 + ((gs - 1) % 5)],
       (ARRAY['l','l','kg','kg','kg'])[1 + ((gs - 1) % 5)],
       20 + (gs % 8),
       'Demo material for maintenance workflow'
FROM generate_series(1, 20) AS gs
ON CONFLICT (code) DO NOTHING;

INSERT INTO equipment_spare_parts (id, created_at, updated_at, is_deleted, equipment_id, spare_part_id, quantity_per_unit, consumption_rate_per_year, position, criticality, notes)
SELECT ('10000000-0000-0000-0000-' || '00000022' || lpad(gs::text, 4, '0'))::uuid,
       now(), now(), false,
       ('00000000-0000-0000-0000-' || '00000001' || lpad((1 + ((gs - 1) % 60))::text, 4, '0'))::uuid,
       ('00000000-0000-0000-0000-' || '00000004' || lpad((1 + ((gs - 1) % 60))::text, 4, '0'))::uuid,
       1 + (gs % 3),
       0.5 + (gs::double precision / 20),
       (ARRAY['main assembly','support assembly','auxiliary'])[1 + ((gs - 1) % 3)],
       (ARRAY['CRITICAL','STANDARD','OPTIONAL'])[1 + ((gs - 1) % 3)],
       'Normative spare requirement'
FROM generate_series(1, 36) AS gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO warehouse_equipment_items (id, created_at, updated_at, is_deleted, warehouse_id, equipment_id, status, active)
SELECT ('10000000-0000-0000-0000-' || '00000023' || lpad(gs::text, 4, '0'))::uuid,
       now(), now(), false,
       ('00000000-0000-0000-0000-' || '00000003000' || (1 + ((gs - 1) % 3))::text)::uuid,
       ('00000000-0000-0000-0000-' || '00000001' || lpad((1 + ((gs - 1) % 60))::text, 4, '0'))::uuid,
       (ARRAY['AVAILABLE','RESERVED','INSTALLED','OUT_OF_SERVICE'])[1 + ((gs - 1) % 4)],
       true
FROM generate_series(1, 24) AS gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO reservations (id, created_at, updated_at, is_deleted, warehouse_stock_id, work_order_id, repair_request_id, reserved_by_id, quantity, status)
SELECT ('10000000-0000-0000-0000-' || '00000024' || lpad(gs::text, 4, '0'))::uuid,
       now(), now(), false,
       ('00000000-0000-0000-0000-' || '00000005' || lpad((100 + (1 + ((gs - 1) % 50)))::text, 4, '0'))::uuid,
       NULL::uuid,
       NULL::uuid,
       '00000000-0000-0000-0000-00000000a006'::uuid,
       1 + (gs % 4),
       (ARRAY['ACTIVE','FULFILLED','CANCELLED'])[1 + ((gs - 1) % 3)]
FROM generate_series(1, 24) AS gs
ON CONFLICT (id) DO NOTHING;

