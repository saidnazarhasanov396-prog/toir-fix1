-- TOIR Navoiyazot-style demo seed draft.
-- Manual runbook draft only. Do not run until backup, dry run, and approvals are complete.
-- This script does not change role permissions and does not create system roles.
-- Demo users below use one shared draft password hash. Security must approve final demo credentials.

-- Stable demo IDs.
-- Departments: 00000000-0000-0000-0000-00000000d001..d004
-- Locations:   00000000-0000-0000-0000-00000000l001 is not valid UUID syntax, so l is represented as a hex digit range a101..a104.
-- Users:       00000000-0000-0000-0000-00000000a001..a010

-- 01. Departments.
INSERT INTO departments (id, created_at, updated_at, is_deleted, code, name, name_en, name_uz, type, parent_id, description)
VALUES
('00000000-0000-0000-0000-00000000d001', now(), now(), false, 'DEMO-NAV', 'JSC Navoiyazot', 'JSC Navoiyazot', 'Navoiyazot AJ', 'ENTERPRISE', NULL, 'Demo enterprise scope'),
('00000000-0000-0000-0000-00000000d002', now(), now(), false, 'DEMO-NAV-AMM', 'Ammonia workshop', 'Ammonia workshop', 'Ammiak sexi', 'WORKSHOP', '00000000-0000-0000-0000-00000000d001', 'Ammonia production maintenance scope'),
('00000000-0000-0000-0000-00000000d003', now(), now(), false, 'DEMO-NAV-UREA', 'Urea workshop', 'Urea workshop', 'Karbamid sexi', 'WORKSHOP', '00000000-0000-0000-0000-00000000d001', 'Urea production maintenance scope'),
('00000000-0000-0000-0000-00000000d004', now(), now(), false, 'DEMO-NAV-HNO3', 'Nitric acid workshop', 'Nitric acid workshop', 'Nitrat kislota sexi', 'WORKSHOP', '00000000-0000-0000-0000-00000000d001', 'Nitric acid maintenance scope')
ON CONFLICT (code) DO NOTHING;

-- 02. Demo users, existing roles, employees, and brigade.
INSERT INTO users (id, username, email, full_name, password_hash, position, phone, status, department_id, primary_role_id, last_login_at, is_deleted, created_at, updated_at)
SELECT v.id, v.username, v.email, v.full_name,
       '$2a$10$7EqJtq98hPqEX7fNZaFWoOhiNRIK.CuUz42fLJKgPFxTYHTJ8kCzO',
       v.position, v.phone, 'ACTIVE', v.department_id, r.id, NULL, false, now(), now()
FROM (VALUES
    ('00000000-0000-0000-0000-00000000a001'::uuid, 'demo_director', 'demo.director@toir.local', 'Demo Technical Director', 'Technical Director', '+998900000001', '00000000-0000-0000-0000-00000000d001'::uuid, 'TECHNICAL_DIRECTOR'),
    ('00000000-0000-0000-0000-00000000a002'::uuid, 'demo_chief_mechanic', 'demo.chief.mechanic@toir.local', 'Demo Chief Mechanic', 'Chief Mechanic', '+998900000002', '00000000-0000-0000-0000-00000000d001'::uuid, 'CHIEF_MECHANIC'),
    ('00000000-0000-0000-0000-00000000a003'::uuid, 'demo_workshop_head', 'demo.workshop.head@toir.local', 'Demo Ammonia Workshop Head', 'Workshop Head', '+998900000003', '00000000-0000-0000-0000-00000000d002'::uuid, 'WORKSHOP_HEAD'),
    ('00000000-0000-0000-0000-00000000a004'::uuid, 'demo_foreman', 'demo.foreman@toir.local', 'Demo Foreman', 'Foreman', '+998900000004', '00000000-0000-0000-0000-00000000d002'::uuid, 'FOREMAN'),
    ('00000000-0000-0000-0000-00000000a005'::uuid, 'demo_ppr_engineer', 'demo.ppr@toir.local', 'Demo PPR Engineer', 'PPR Engineer', '+998900000005', '00000000-0000-0000-0000-00000000d001'::uuid, 'PPR_ENGINEER'),
    ('00000000-0000-0000-0000-00000000a006'::uuid, 'demo_storekeeper', 'demo.storekeeper@toir.local', 'Demo Storekeeper', 'Storekeeper', '+998900000006', '00000000-0000-0000-0000-00000000d001'::uuid, 'STOREKEEPER'),
    ('00000000-0000-0000-0000-00000000a007'::uuid, 'demo_supply', 'demo.supply@toir.local', 'Demo Supply Specialist', 'Supply Specialist', '+998900000007', '00000000-0000-0000-0000-00000000d001'::uuid, 'SUPPLY_SPECIALIST'),
    ('00000000-0000-0000-0000-00000000a008'::uuid, 'demo_economist', 'demo.economist@toir.local', 'Demo Economist', 'Economist', '+998900000008', '00000000-0000-0000-0000-00000000d001'::uuid, 'ECONOMIST'),
    ('00000000-0000-0000-0000-00000000a009'::uuid, 'demo_reliability', 'demo.reliability@toir.local', 'Demo Reliability Engineer', 'Reliability Engineer', '+998900000009', '00000000-0000-0000-0000-00000000d001'::uuid, 'RELIABILITY_ENGINEER'),
    ('00000000-0000-0000-0000-00000000a010'::uuid, 'demo_viewer', 'demo.viewer@toir.local', 'Demo Viewer', 'Viewer', '+998900000010', '00000000-0000-0000-0000-00000000d002'::uuid, 'VIEWER')
) AS v(id, username, email, full_name, position, phone, department_id, role_code)
JOIN roles r ON r.code = v.role_code AND r.is_deleted = false
ON CONFLICT (username) DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM (VALUES
    ('demo_director', 'TECHNICAL_DIRECTOR'),
    ('demo_chief_mechanic', 'CHIEF_MECHANIC'),
    ('demo_workshop_head', 'WORKSHOP_HEAD'),
    ('demo_foreman', 'FOREMAN'),
    ('demo_ppr_engineer', 'PPR_ENGINEER'),
    ('demo_storekeeper', 'STOREKEEPER'),
    ('demo_supply', 'SUPPLY_SPECIALIST'),
    ('demo_economist', 'ECONOMIST'),
    ('demo_reliability', 'RELIABILITY_ENGINEER'),
    ('demo_viewer', 'VIEWER')
) AS v(username, role_code)
JOIN users u ON u.username = v.username AND u.is_deleted = false
JOIN roles r ON r.code = v.role_code AND r.is_deleted = false
WHERE NOT EXISTS (
    SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role_id = r.id
);

INSERT INTO hr_employees (id, created_at, updated_at, is_deleted, personnel_number, first_name, last_name, middle_name, position, department_id, brigade_id, user_id, hire_date, terminated_date, grade, phone, email, is_active)
VALUES
('00000000-0000-0000-0000-00000000e001', now(), now(), false, 'DEMO-EMP-001', 'Demo', 'Director', NULL, 'Technical Director', '00000000-0000-0000-0000-00000000d001', NULL, '00000000-0000-0000-0000-00000000a001', DATE '2020-01-10', NULL, 'M1', '+998900000001', 'demo.director@toir.local', true),
('00000000-0000-0000-0000-00000000e002', now(), now(), false, 'DEMO-EMP-002', 'Demo', 'Mechanic', NULL, 'Chief Mechanic', '00000000-0000-0000-0000-00000000d001', NULL, '00000000-0000-0000-0000-00000000a002', DATE '2019-03-12', NULL, 'M1', '+998900000002', 'demo.chief.mechanic@toir.local', true),
('00000000-0000-0000-0000-00000000e003', now(), now(), false, 'DEMO-EMP-003', 'Demo', 'Workshop', NULL, 'Workshop Head', '00000000-0000-0000-0000-00000000d002', NULL, '00000000-0000-0000-0000-00000000a003', DATE '2021-06-01', NULL, 'M2', '+998900000003', 'demo.workshop.head@toir.local', true),
('00000000-0000-0000-0000-00000000e004', now(), now(), false, 'DEMO-EMP-004', 'Demo', 'Foreman', NULL, 'Foreman', '00000000-0000-0000-0000-00000000d002', NULL, '00000000-0000-0000-0000-00000000a004', DATE '2022-02-15', NULL, '6', '+998900000004', 'demo.foreman@toir.local', true),
('00000000-0000-0000-0000-00000000e005', now(), now(), false, 'DEMO-EMP-005', 'Demo', 'Storekeeper', NULL, 'Storekeeper', '00000000-0000-0000-0000-00000000d001', NULL, '00000000-0000-0000-0000-00000000a006', DATE '2021-11-01', NULL, '5', '+998900000006', 'demo.storekeeper@toir.local', true)
ON CONFLICT (personnel_number) DO NOTHING;

INSERT INTO brigades (id, created_at, updated_at, is_deleted, code, name, department_id, foreman_id, is_active, specialization)
VALUES ('00000000-0000-0000-0000-00000000b001', now(), now(), false, 'DEMO-BR-AMM-MECH', 'Demo ammonia mechanical brigade', '00000000-0000-0000-0000-00000000d002', '00000000-0000-0000-0000-00000000a004', true, 'Rotating equipment and mechanical repairs')
ON CONFLICT (code) DO NOTHING;

INSERT INTO brigade_members (id, created_at, updated_at, is_deleted, brigade_id, user_id, role_code, grade, qualifications, is_active)
VALUES
('00000000-0000-0000-0000-00000000b101', now(), now(), false, '00000000-0000-0000-0000-00000000b001', '00000000-0000-0000-0000-00000000a004', 'FOREMAN', 6, '["ROTATING_EQUIPMENT","HOT_WORK"]'::jsonb, true)
ON CONFLICT (brigade_id, user_id) DO NOTHING;

-- 03. Reference data.
INSERT INTO units_of_measurement (id, created_at, updated_at, is_deleted, code, name, name_en, name_uz)
VALUES
('00000000-0000-0000-0000-00000000c001', now(), now(), false, 'DEMO-PC', 'piece', 'piece', 'dona'),
('00000000-0000-0000-0000-00000000c002', now(), now(), false, 'DEMO-KG', 'kilogram', 'kilogram', 'kilogram')
ON CONFLICT (code) DO NOTHING;

INSERT INTO cost_categories (id, created_at, updated_at, is_deleted, code, name, name_en, name_uz, description)
VALUES
('00000000-0000-0000-0000-00000000c101', now(), now(), false, 'DEMO-MATERIALS', 'Materials', 'Materials', 'Materiallar', 'Demo material and spare part cost category'),
('00000000-0000-0000-0000-00000000c102', now(), now(), false, 'DEMO-LABOR', 'Labor', 'Labor', 'Mehnat', 'Demo internal labor cost category')
ON CONFLICT (code) DO NOTHING;

INSERT INTO defect_categories (id, created_at, updated_at, is_deleted, code, name, name_en, name_uz, description)
VALUES ('00000000-0000-0000-0000-00000000c201', now(), now(), false, 'DEMO-BEARING_WEAR', 'Bearing wear', 'Bearing wear', 'Podshipnik yeyilishi', 'Demo rotating equipment defect category')
ON CONFLICT (code) DO NOTHING;

INSERT INTO defect_severities (id, created_at, updated_at, is_deleted, code, name, name_en, name_uz, weight)
VALUES ('00000000-0000-0000-0000-00000000c301', now(), now(), false, 'DEMO-MAJOR', 'Major', 'Major', 'Jiddiy', 4)
ON CONFLICT (code) DO NOTHING;

INSERT INTO failure_reasons (id, created_at, updated_at, is_deleted, code, name, name_en, name_uz, description)
VALUES ('00000000-0000-0000-0000-00000000c401', now(), now(), false, 'DEMO-MECH_WEAR', 'Mechanical wear', 'Mechanical wear', 'Mexanik yeyilish', 'Demo failure reason')
ON CONFLICT (code) DO NOTHING;

INSERT INTO root_causes (id, created_at, updated_at, is_deleted, code, name, name_en, name_uz, description)
VALUES ('00000000-0000-0000-0000-00000000c501', now(), now(), false, 'DEMO-LUBRICATION', 'Insufficient lubrication', 'Insufficient lubrication', 'Yetarli moylanmagan', 'Demo root cause')
ON CONFLICT (code) DO NOTHING;

INSERT INTO criticality_classes (id, created_at, updated_at, is_deleted, code, name, name_en, name_uz, level, description, safety_impact, production_impact, ecological_impact, energy_impact, failure_consequence, repair_priority)
VALUES ('00000000-0000-0000-0000-00000000c601', now(), now(), false, 'DEMO-A', 'Critical class A', 'Critical class A', 'A kritik sinf', 'CRITICAL', 'Production stop or safety impact', 5, 5, 4, 3, 'Unit shutdown risk', 1)
ON CONFLICT (code) DO NOTHING;

-- 04. Locations.
INSERT INTO locations (id, created_at, updated_at, is_deleted, code, name, name_en, name_uz, type, parent_id, department_id, description)
VALUES
('00000000-0000-0000-0000-00000000a101', now(), now(), false, 'DEMO-LOC-PLANT', 'Navoiyazot demo site', 'Navoiyazot demo site', 'Navoiyazot demo maydoni', 'SITE', NULL, '00000000-0000-0000-0000-00000000d001', 'Demo site'),
('00000000-0000-0000-0000-00000000a102', now(), now(), false, 'DEMO-LOC-AMM-COMP', 'Ammonia compression area', 'Ammonia compression area', 'Ammiak kompressiya maydoni', 'SECTION', '00000000-0000-0000-0000-00000000a101', '00000000-0000-0000-0000-00000000d002', 'Compressor and pump area'),
('00000000-0000-0000-0000-00000000a103', now(), now(), false, 'DEMO-LOC-UREA-GRAN', 'Urea granulation area', 'Urea granulation area', 'Karbamid granulyatsiya maydoni', 'SECTION', '00000000-0000-0000-0000-00000000a101', '00000000-0000-0000-0000-00000000d003', 'Granulation area'),
('00000000-0000-0000-0000-00000000a104', now(), now(), false, 'DEMO-LOC-MAIN-WH', 'Central warehouse', 'Central warehouse', 'Markaziy ombor', 'WAREHOUSE', '00000000-0000-0000-0000-00000000a101', '00000000-0000-0000-0000-00000000d001', 'Demo central warehouse')
ON CONFLICT (code) DO NOTHING;

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
ON CONFLICT (code) DO NOTHING;

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

-- 08. PPR plans and tasks.
INSERT INTO ppr_plans (id, created_at, updated_at, is_deleted, code, name, year, month, status, department_id, created_by_id, approved_by_id, notes)
VALUES ('00000000-0000-0000-0000-000000060001', now(), now(), false, 'DEMO-PPR-2026-05-AMM', 'Demo May 2026 PPR plan for ammonia workshop', 2026, 5, 'APPROVED', '00000000-0000-0000-0000-00000000d002', '00000000-0000-0000-0000-00000000a005', '00000000-0000-0000-0000-00000000a002', 'Demo approved monthly PPR plan')
ON CONFLICT (code) DO NOTHING;

INSERT INTO ppr_tasks (id, created_at, updated_at, is_deleted, code, plan_id, regulation_id, equipment_id, title, scheduled_start, scheduled_end, due_date, status, priority, planned_labor_hours, actual_labor_hours, postpone_reason)
VALUES
('00000000-0000-0000-0000-000000060101', now(), now(), false, 'DEMO-PPR-TASK-001', '00000000-0000-0000-0000-000000060001', '00000000-0000-0000-0000-000000020002', '00000000-0000-0000-0000-000000010001', 'Quarterly compressor vibration and oil system check', TIMESTAMP '2026-05-21 08:00:00', TIMESTAMP '2026-05-21 16:00:00', TIMESTAMP '2026-05-21 16:00:00', 'APPROVED', 'HIGH', 16.0, NULL, NULL),
('00000000-0000-0000-0000-000000060102', now(), now(), false, 'DEMO-PPR-TASK-002', '00000000-0000-0000-0000-000000060001', '00000000-0000-0000-0000-000000020001', '00000000-0000-0000-0000-000000010002', 'Monthly condensate pump lubrication', TIMESTAMP '2026-05-22 09:00:00', TIMESTAMP '2026-05-22 13:00:00', TIMESTAMP '2026-05-22 13:00:00', 'PLANNED', 'MEDIUM', 4.0, NULL, NULL)
ON CONFLICT (code) DO NOTHING;

-- 09. Repair requests.
INSERT INTO repair_requests (id, created_at, updated_at, is_deleted, number, title, description, equipment_id, department_id, location_id, reporter_id, assigned_to_id, priority, criticality, status, source, detected_at, target_completion_at, actual_completion_at, reacted_at, rejection_reason, clarification_reason, close_result)
VALUES
('00000000-0000-0000-0000-000000070001', now(), now(), false, 'DEMO-RR-2026-0001', 'Seal leak on condensate pump P-101', 'Operator reported visible leakage at mechanical seal and rising bearing temperature.', '00000000-0000-0000-0000-000000010002', '00000000-0000-0000-0000-00000000d002', '00000000-0000-0000-0000-00000000a102', '00000000-0000-0000-0000-00000000a003', '00000000-0000-0000-0000-00000000a004', 'HIGH', 'HIGH', 'ASSIGNED', 'MANUAL', now() - interval '2 days', now() + interval '1 day', NULL, now() - interval '1 day', NULL, NULL, NULL),
('00000000-0000-0000-0000-000000070002', now(), now(), false, 'DEMO-RR-2026-0002', 'Compressor K-1 vibration warning', 'Vibration trend reached warning level on drive-end bearing.', '00000000-0000-0000-0000-000000010001', '00000000-0000-0000-0000-00000000d002', '00000000-0000-0000-0000-00000000a102', '00000000-0000-0000-0000-00000000a009', '00000000-0000-0000-0000-00000000a004', 'CRITICAL', 'CRITICAL', 'IN_PROGRESS', 'INSPECTION', now() - interval '1 day', now() + interval '8 hours', NULL, now() - interval '20 hours', NULL, NULL, NULL)
ON CONFLICT (number) DO NOTHING;

-- 10. Work orders.
INSERT INTO work_orders (id, created_at, updated_at, is_deleted, number, title, equipment_id, department_id, repair_request_id, defect_id, ppr_task_id, contractor_id, warehouse_id, replacement_equipment_id, status, type, work_type, priority, start_planned_at, end_planned_at, started_at, completed_at, summary, result, closure_notes, created_by_id, approved_by_id)
VALUES
('00000000-0000-0000-0000-000000080001', now(), now(), false, 'DEMO-WO-2026-0001', 'Replace mechanical seal on P-101', '00000000-0000-0000-0000-000000010002', '00000000-0000-0000-0000-00000000d002', '00000000-0000-0000-0000-000000070001', NULL, NULL, NULL, '00000000-0000-0000-0000-000000030001', NULL, 'IN_PROGRESS', 'DEFECT', 'REPAIR', 'HIGH', now() - interval '1 day', now() + interval '8 hours', now() - interval '4 hours', NULL, 'Seal replacement in progress', NULL, NULL, '00000000-0000-0000-0000-00000000a003', '00000000-0000-0000-0000-00000000a002'),
('00000000-0000-0000-0000-000000080002', now(), now(), false, 'DEMO-WO-2026-0002', 'Quarterly maintenance on K-1', '00000000-0000-0000-0000-000000010001', '00000000-0000-0000-0000-00000000d002', NULL, NULL, '00000000-0000-0000-0000-000000060101', NULL, NULL, NULL, 'APPROVED', 'PLANNED', 'DIAGNOSTICS', 'HIGH', now() + interval '1 day', now() + interval '2 days', NULL, NULL, 'Approved PPR work order', NULL, NULL, '00000000-0000-0000-0000-00000000a005', '00000000-0000-0000-0000-00000000a002')
ON CONFLICT (number) DO NOTHING;

INSERT INTO work_order_tasks (id, created_at, updated_at, is_deleted, work_order_id, title, description, status, assigned_to_id, planned_hours, actual_hours, started_at, completed_at)
VALUES
('00000000-0000-0000-0000-000000080101', now(), now(), false, '00000000-0000-0000-0000-000000080001', 'Isolate and drain pump', 'Apply lockout and drain the pump casing.', 'DONE', '00000000-0000-0000-0000-00000000a004', 1.0, 1.0, now() - interval '4 hours', now() - interval '3 hours'),
('00000000-0000-0000-0000-000000080102', now(), now(), false, '00000000-0000-0000-0000-000000080001', 'Replace seal cartridge', 'Replace mechanical seal and inspect shaft sleeve.', 'IN_PROGRESS', '00000000-0000-0000-0000-00000000a004', 3.0, NULL, now() - interval '2 hours', NULL)
ON CONFLICT (id) DO NOTHING;

INSERT INTO stock_movements (id, created_at, updated_at, is_deleted, warehouse_id, spare_part_id, work_order_id, type, quantity, unit_cost, document_number, created_by_id, occurred_at, notes)
VALUES ('00000000-0000-0000-0000-000000080201', now(), now(), false, '00000000-0000-0000-0000-000000030001', '00000000-0000-0000-0000-000000040002', '00000000-0000-0000-0000-000000080001', 'ISSUE', 1, 245000.00, 'DEMO-SM-2026-0001', '00000000-0000-0000-0000-00000000a006', now() - interval '2 hours', 'Issued mechanical seal for demo work order')
ON CONFLICT (id) DO NOTHING;

-- 11. Defects and defect lists.
INSERT INTO defects (id, created_at, updated_at, is_deleted, code, title, description, equipment_id, repair_request_id, category, severity, failure_reason, root_cause, status, detected_at, resolved_at, recurrence_count)
VALUES ('00000000-0000-0000-0000-000000090001', now(), now(), false, 'DEMO-DEF-2026-0001', 'Bearing wear trend on compressor K-1', 'Drive-end bearing vibration growth requires inspection during next PPR window.', '00000000-0000-0000-0000-000000010001', '00000000-0000-0000-0000-000000070002', 'DEMO-BEARING_WEAR', 'DEMO-MAJOR', 'DEMO-MECH_WEAR', 'DEMO-LUBRICATION', 'IN_ANALYSIS', now() - interval '1 day', NULL, 1)
ON CONFLICT (code) DO NOTHING;

INSERT INTO defect_lists (id, created_at, updated_at, is_deleted, code, title, equipment_id, repair_request_id, work_order_id, created_by_id, approved_by_id, status, total_labor_hours, total_estimated_cost, notes)
VALUES ('00000000-0000-0000-0000-000000090101', now(), now(), false, 'DEMO-DL-2026-0001', 'Compressor K-1 bearing inspection scope', '00000000-0000-0000-0000-000000010001', '00000000-0000-0000-0000-000000070002', '00000000-0000-0000-0000-000000080002', '00000000-0000-0000-0000-00000000a009', '00000000-0000-0000-0000-00000000a002', 'APPROVED', 8.0, 1250000.00, 'Demo defect list for PPR-linked work')
ON CONFLICT (code) DO NOTHING;

INSERT INTO defect_list_lines (id, created_at, updated_at, is_deleted, defect_list_id, defect_id, description, work_scope, material_specification, spare_part_id, required_quantity, estimated_labor_hours, estimated_cost)
VALUES ('00000000-0000-0000-0000-000000090201', now(), now(), false, '00000000-0000-0000-0000-000000090101', '00000000-0000-0000-0000-000000090001', 'Bearing inspection and vibration verification', 'Inspect bearing housing, verify lubrication path, retake vibration readings', 'Bearing 6208 if replacement required', '00000000-0000-0000-0000-000000040001', 2, 8.0, 1250000.00)
ON CONFLICT (id) DO NOTHING;

-- 12. Procurement.
INSERT INTO procurement_requests (id, created_at, updated_at, is_deleted, number, title, description, department_id, warehouse_id, requested_by, approved_by, status, source, required_by, total_estimated_cost, submitted_at, approved_at, ordered_at, received_at, rejection_reason)
VALUES ('00000000-0000-0000-0000-0000000a0001', now(), now(), false, 'DEMO-PR-2026-0001', 'Replenish low stock mechanical seals', 'Generated from low stock level in central warehouse.', '00000000-0000-0000-0000-00000000d001', '00000000-0000-0000-0000-000000030001', '00000000-0000-0000-0000-00000000a007', '00000000-0000-0000-0000-00000000a002', 'APPROVED', 'AUTO', DATE '2026-05-30', 1960000.00, now() - interval '1 day', now() - interval '12 hours', NULL, NULL, NULL)
ON CONFLICT (number) DO NOTHING;

INSERT INTO procurement_request_lines (id, created_at, updated_at, is_deleted, request_id, spare_part_id, quantity, unit, unit_price, estimated_cost, notes)
VALUES ('00000000-0000-0000-0000-0000000a0101', now(), now(), false, '00000000-0000-0000-0000-0000000a0001', '00000000-0000-0000-0000-000000040002', 8, 'pc', 245000.00, 1960000.00, 'Bring stock above reorder point')
ON CONFLICT (id) DO NOTHING;

-- 13. Finance.
INSERT INTO maintenance_budgets (id, created_at, updated_at, is_deleted, year, month, department_id, status, total_planned, total_actual)
VALUES ('00000000-0000-0000-0000-0000000b0001', now(), now(), false, 2026, 5, '00000000-0000-0000-0000-00000000d002', 'APPROVED', 50000000.00, 245000.00)
ON CONFLICT (id) DO NOTHING;

INSERT INTO budget_lines (id, created_at, updated_at, is_deleted, budget_id, cost_category_id, description, planned_amount, actual_amount)
VALUES ('00000000-0000-0000-0000-0000000b0101', now(), now(), false, '00000000-0000-0000-0000-0000000b0001', '00000000-0000-0000-0000-00000000c101', 'Demo ammonia spare part budget', 35000000.00, 245000.00)
ON CONFLICT (id) DO NOTHING;

INSERT INTO actual_costs (id, created_at, updated_at, is_deleted, work_order_id, repair_request_id, contractor_work_id, budget_line_id, cost_category_id, status, reviewed_by_id, reviewed_at, review_comment, amount, cost_date, notes)
VALUES ('00000000-0000-0000-0000-0000000b0201', now(), now(), false, '00000000-0000-0000-0000-000000080001', '00000000-0000-0000-0000-000000070001', NULL, '00000000-0000-0000-0000-0000000b0101', '00000000-0000-0000-0000-00000000c101', 'APPROVED', '00000000-0000-0000-0000-00000000a008', now() - interval '1 hour', 'Approved for demo work order material issue', 245000.00, now() - interval '2 hours', 'Mechanical seal issued to work order')
ON CONFLICT (id) DO NOTHING;

-- 14. Approvals.
INSERT INTO approval_requests (id, created_at, updated_at, is_deleted, document_type, document_id, title, requester_id, status, current_step, completed_at, description)
VALUES ('00000000-0000-0000-0000-0000000c0001', now(), now(), false, 'PROCUREMENT_REQUEST', '00000000-0000-0000-0000-0000000a0001', 'Approve demo low-stock procurement request', '00000000-0000-0000-0000-00000000a007', 'PENDING', 1, NULL, 'Demo approval awaiting chief mechanic review')
ON CONFLICT (id) DO NOTHING;

INSERT INTO approval_steps (id, created_at, updated_at, is_deleted, request_id, step_number, approver_id, approver_role, decision, decided_at, comment)
VALUES
('00000000-0000-0000-0000-0000000c0101', now(), now(), false, '00000000-0000-0000-0000-0000000c0001', 1, '00000000-0000-0000-0000-00000000a002', 'CHIEF_MECHANIC', 'PENDING', NULL, NULL),
('00000000-0000-0000-0000-0000000c0102', now(), now(), false, '00000000-0000-0000-0000-0000000c0001', 2, '00000000-0000-0000-0000-00000000a001', 'TECHNICAL_DIRECTOR', 'PENDING', NULL, NULL)
ON CONFLICT (request_id, step_number) DO NOTHING;

-- 15. Inspections.
INSERT INTO inspection_routes (id, created_at, updated_at, is_deleted, code, name, department_id, frequency, target_duration_min, description, is_active)
VALUES ('00000000-0000-0000-0000-0000000d0001', now(), now(), false, 'DEMO-IR-AMM-SHIFT', 'Demo ammonia shift inspection', '00000000-0000-0000-0000-00000000d002', 'SHIFT', 45, 'Shift inspection route for ammonia compressor area', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO inspection_checkpoints (id, created_at, updated_at, is_deleted, route_id, order_index, equipment_id, location_id, title, instruction, check_type, expected_min, expected_max, expected_unit, is_mandatory)
VALUES
('00000000-0000-0000-0000-0000000d0101', now(), now(), false, '00000000-0000-0000-0000-0000000d0001', 1, '00000000-0000-0000-0000-000000010001', '00000000-0000-0000-0000-00000000a102', 'Check compressor vibration', 'Measure vibration on drive-end bearing.', 'MEASUREMENT', 0, 4.5, 'mm/s', true),
('00000000-0000-0000-0000-0000000d0102', now(), now(), false, '00000000-0000-0000-0000-0000000d0001', 2, '00000000-0000-0000-0000-000000010002', '00000000-0000-0000-0000-00000000a102', 'Inspect pump seal area', 'Check leakage and bearing temperature.', 'VISUAL', NULL, NULL, NULL, true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO inspection_rounds (id, created_at, updated_at, is_deleted, route_id, performed_by, started_at, completed_at, status, findings_count, alarm_count, notes)
VALUES ('00000000-0000-0000-0000-0000000d0201', now(), now(), false, '00000000-0000-0000-0000-0000000d0001', '00000000-0000-0000-0000-00000000a004', now() - interval '6 hours', now() - interval '5 hours', 'COMPLETED', 1, 1, 'Demo completed shift round')
ON CONFLICT (id) DO NOTHING;

INSERT INTO inspection_round_results (id, created_at, updated_at, is_deleted, round_id, checkpoint_id, status, measured_value, measured_unit, comment, defect_id, photo_file_ids)
VALUES
('00000000-0000-0000-0000-0000000d0301', now(), now(), false, '00000000-0000-0000-0000-0000000d0201', '00000000-0000-0000-0000-0000000d0101', 'WARN', 4.8, 'mm/s', 'Above warning threshold, defect registered.', '00000000-0000-0000-0000-000000090001', '[]'::jsonb),
('00000000-0000-0000-0000-0000000d0302', now(), now(), false, '00000000-0000-0000-0000-0000000d0201', '00000000-0000-0000-0000-0000000d0102', 'OK', NULL, NULL, 'No visible external leakage after isolation.', NULL, '[]'::jsonb)
ON CONFLICT (id) DO NOTHING;

-- 16. Knowledge.
INSERT INTO knowledge_articles (id, created_at, updated_at, is_deleted, code, title, kind, equipment_type_id, equipment_id, defect_id, work_order_id, problem, root_cause, solution, preventive_actions, tags, author_id, view_count)
VALUES
('00000000-0000-0000-0000-0000000e0001', now(), now(), false, 'DEMO-KB-COMP-VIBRATION', 'Compressor vibration early response', 'LESSON_LEARNED', '00000000-0000-0000-0000-00000000f002', '00000000-0000-0000-0000-000000010001', '00000000-0000-0000-0000-000000090001', '00000000-0000-0000-0000-000000080002', 'Vibration warning can develop into bearing failure if not investigated during the same shift.', 'Insufficient lubrication and delayed trend review.', 'Create high-priority inspection task, verify lubrication route, and plan bearing inspection.', 'Weekly vibration trend review and lubrication checklist verification.', '["demo","compressor","vibration","bearing"]'::jsonb, '00000000-0000-0000-0000-00000000a009', 12),
('00000000-0000-0000-0000-0000000e0002', now(), now(), false, 'DEMO-KB-PUMP-SEAL', 'Pump mechanical seal replacement checklist', 'PROCEDURE', '00000000-0000-0000-0000-00000000f001', '00000000-0000-0000-0000-000000010002', NULL, '00000000-0000-0000-0000-000000080001', 'Seal leakage caused product loss and bearing contamination risk.', 'Seal wear under unstable operating mode.', 'Isolate pump, replace seal cartridge, verify shaft sleeve, run leak test.', 'Keep minimum seal stock and inspect during monthly PPR.', '["demo","pump","seal","procedure"]'::jsonb, '00000000-0000-0000-0000-00000000a002', 8)
ON CONFLICT (code) DO NOTHING;
