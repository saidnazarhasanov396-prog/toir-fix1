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

-- 17. Production-like demo volume expansion.
-- Set-based inserts keep the dataset deterministic while avoiding hundreds of hand-written rows.

-- 17.01. Organization, locations, role users, employees, brigades.
INSERT INTO departments (id, created_at, updated_at, is_deleted, code, name, name_en, name_uz, type, parent_id, description)
VALUES ('00000000-0000-0000-0000-00000000d005', now(), now(), false, 'DEMO-NAV-MECH', 'Mechanical repair workshop', 'Mechanical repair workshop', 'Mexanik tamirlash sexi', 'WORKSHOP', '00000000-0000-0000-0000-00000000d001', 'Central repair workshop for rotating, electrical, and instrument work')
ON CONFLICT (code) DO NOTHING;

INSERT INTO locations (id, created_at, updated_at, is_deleted, code, name, name_en, name_uz, type, parent_id, department_id, description)
VALUES
('00000000-0000-0000-0000-00000000a105', now(), now(), false, 'DEMO-LOC-AMM-SYN', 'Ammonia synthesis section', 'Ammonia synthesis section', 'Ammiak sintez uchastkasi', 'SECTION', '00000000-0000-0000-0000-00000000a101', '00000000-0000-0000-0000-00000000d002', 'Synthesis loop'),
('00000000-0000-0000-0000-00000000a106', now(), now(), false, 'DEMO-LOC-AMM-PUMP', 'Ammonia pump gallery', 'Ammonia pump gallery', 'Ammiak nasos galereyasi', 'SECTION', '00000000-0000-0000-0000-00000000a101', '00000000-0000-0000-0000-00000000d002', 'Pump gallery'),
('00000000-0000-0000-0000-00000000a107', now(), now(), false, 'DEMO-LOC-UREA-SYN', 'Urea synthesis section', 'Urea synthesis section', 'Karbamid sintez uchastkasi', 'SECTION', '00000000-0000-0000-0000-00000000a101', '00000000-0000-0000-0000-00000000d003', 'High pressure synthesis'),
('00000000-0000-0000-0000-00000000a108', now(), now(), false, 'DEMO-LOC-UREA-BAG', 'Urea bagging line', 'Urea bagging line', 'Karbamid qadoqlash liniyasi', 'LINE', '00000000-0000-0000-0000-00000000a101', '00000000-0000-0000-0000-00000000d003', 'Bagging line'),
('00000000-0000-0000-0000-00000000a109', now(), now(), false, 'DEMO-LOC-HNO3-OX', 'Nitric acid oxidation section', 'Nitric acid oxidation section', 'Azot kislotasi oksidlash uchastkasi', 'SECTION', '00000000-0000-0000-0000-00000000a101', '00000000-0000-0000-0000-00000000d004', 'Oxidation section'),
('00000000-0000-0000-0000-00000000a110', now(), now(), false, 'DEMO-LOC-HNO3-ABS', 'Nitric acid absorption area', 'Nitric acid absorption area', 'Azot kislotasi absorbsiya maydoni', 'ZONE', '00000000-0000-0000-0000-00000000a101', '00000000-0000-0000-0000-00000000d004', 'Absorption area'),
('00000000-0000-0000-0000-00000000a111', now(), now(), false, 'DEMO-LOC-MECH-SHOP', 'Mechanical repair bay', 'Mechanical repair bay', 'Mexanik tamirlash posti', 'BUILDING', '00000000-0000-0000-0000-00000000a101', '00000000-0000-0000-0000-00000000d005', 'Repair bay'),
('00000000-0000-0000-0000-00000000a112', now(), now(), false, 'DEMO-LOC-MECH-WH', 'Mechanical workshop store', 'Mechanical workshop store', 'Mexanik sex ombori', 'WAREHOUSE', '00000000-0000-0000-0000-00000000a101', '00000000-0000-0000-0000-00000000d005', 'Workshop store')
ON CONFLICT (code) DO NOTHING;

INSERT INTO cost_categories (id, created_at, updated_at, is_deleted, code, name, name_en, name_uz, description)
VALUES
('00000000-0000-0000-0000-00000000c103', now(), now(), false, 'DEMO-CONTRACTOR', 'Contractor services', 'Contractor services', 'Pudratchi xizmatlari', 'Demo external contractor maintenance cost category'),
('00000000-0000-0000-0000-00000000c104', now(), now(), false, 'DEMO-INSPECTION', 'Inspection and diagnostics', 'Inspection and diagnostics', 'Tekshiruv va diagnostika', 'Demo inspection and diagnostics cost category'),
('00000000-0000-0000-0000-00000000c105', now(), now(), false, 'DEMO-EMERGENCY', 'Emergency repair reserve', 'Emergency repair reserve', 'Favqulodda tamirlash zaxirasi', 'Demo emergency repair cost category')
ON CONFLICT (code) DO NOTHING;

INSERT INTO users (id, username, email, full_name, password_hash, position, phone, status, department_id, primary_role_id, last_login_at, is_deleted, created_at, updated_at)
SELECT v.id, v.username, v.email, v.full_name, '$2a$10$7EqJtq98hPqEX7fNZaFWoOhiNRIK.CuUz42fLJKgPFxTYHTJ8kCzO', v.position, v.phone, 'ACTIVE', v.department_id, r.id, NULL, false, now(), now()
FROM (VALUES
    ('00000000-0000-0000-0000-00000000a011'::uuid, 'demo_section_head', 'demo.section.head@toir.local', 'Demo Section Head', 'Section Head', '+998900000011', '00000000-0000-0000-0000-00000000d002'::uuid, 'SECTION_HEAD'),
    ('00000000-0000-0000-0000-00000000a012'::uuid, 'demo_mech_workshop', 'demo.mech.workshop@toir.local', 'Demo Mechanical Workshop Head', 'Mechanical Workshop Head', '+998900000012', '00000000-0000-0000-0000-00000000d005'::uuid, 'WORKSHOP_HEAD')
) AS v(id, username, email, full_name, position, phone, department_id, role_code)
JOIN roles r ON r.code = v.role_code AND r.is_deleted = false
ON CONFLICT (username) DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM (VALUES ('demo_section_head', 'SECTION_HEAD'), ('demo_mech_workshop', 'WORKSHOP_HEAD')) AS v(username, role_code)
JOIN users u ON u.username = v.username AND u.is_deleted = false
JOIN roles r ON r.code = v.role_code AND r.is_deleted = false
WHERE NOT EXISTS (SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role_id = r.id);

INSERT INTO hr_employees (id, created_at, updated_at, is_deleted, personnel_number, first_name, last_name, middle_name, position, department_id, brigade_id, user_id, hire_date, terminated_date, grade, phone, email, is_active)
SELECT ('00000000-0000-0000-0000-' || '00000000e' || lpad(gs::text, 3, '0'))::uuid, now(), now(), false,
       'DEMO-EMP-' || lpad(gs::text, 3, '0'),
       (ARRAY['Aziz','Bekzod','Dilshod','Jasur','Nodir','Oybek','Sardor','Timur','Umid','Zafar'])[1 + ((gs - 1) % 10)],
       (ARRAY['Karimov','Usmonov','Tursunov','Yuldashev','Rakhimov','Akramov','Ismoilov','Kurbanov','Nazarov','Saidov'])[1 + ((gs - 1) % 10)],
       NULL,
       (ARRAY['Mechanic','Electrician','Instrumentation technician','Welder','Planner','Warehouse operator','Reliability engineer','Pump specialist'])[1 + ((gs - 1) % 8)],
       (ARRAY['00000000-0000-0000-0000-00000000d002','00000000-0000-0000-0000-00000000d003','00000000-0000-0000-0000-00000000d004','00000000-0000-0000-0000-00000000d005'])[1 + ((gs - 6) % 4)]::uuid,
       NULL, NULL, DATE '2018-01-01' + (gs * 37), NULL, (4 + (gs % 4))::text,
       '+998900001' || lpad(gs::text, 3, '0'), 'demo.employee' || lpad(gs::text, 3, '0') || '@toir.local', true
FROM generate_series(6, 25) AS gs
ON CONFLICT (personnel_number) DO NOTHING;

INSERT INTO brigades (id, created_at, updated_at, is_deleted, code, name, department_id, foreman_id, is_active, specialization)
SELECT ('00000000-0000-0000-0000-' || '00000000b' || lpad(gs::text, 3, '0'))::uuid, now(), now(), false,
       (ARRAY['DEMO-BR-UREA-MECH','DEMO-BR-HNO3-MECH','DEMO-BR-MECH-OVERHAUL','DEMO-BR-ELECTRICAL','DEMO-BR-INSTRUMENT'])[gs - 1],
       (ARRAY['Urea mechanical brigade','Nitric acid mechanical brigade','Central overhaul brigade','Electrical repair brigade','Instrumentation and metrology brigade'])[gs - 1],
       (ARRAY['00000000-0000-0000-0000-00000000d003','00000000-0000-0000-0000-00000000d004','00000000-0000-0000-0000-00000000d005','00000000-0000-0000-0000-00000000d005','00000000-0000-0000-0000-00000000d005'])[gs - 1]::uuid,
       '00000000-0000-0000-0000-00000000a004'::uuid, true,
       (ARRAY['Urea rotating equipment','Nitric acid pumps and heat exchangers','Capital repair and replacements','Motors and electrical equipment','Instrumentation and control valves'])[gs - 1]
FROM generate_series(2, 6) AS gs
ON CONFLICT (code) DO NOTHING;

INSERT INTO brigade_members (id, created_at, updated_at, is_deleted, brigade_id, user_id, role_code, grade, qualifications, is_active)
SELECT ('00000000-0000-0000-0000-' || '00000000b' || lpad((200 + gs)::text, 3, '0'))::uuid, now(), now(), false,
       ('00000000-0000-0000-0000-' || '00000000b' || lpad((1 + ((gs - 1) % 6))::text, 3, '0'))::uuid,
       (ARRAY['00000000-0000-0000-0000-00000000a004','00000000-0000-0000-0000-00000000a011','00000000-0000-0000-0000-00000000a012'])[1 + ((gs - 1) % 3)]::uuid,
       (ARRAY['FOREMAN','LOCKSMITH','ELECTRICIAN','INSTRUMENT','WELDER','HELPER'])[1 + ((gs - 1) % 6)],
       4 + (gs % 4), '["ROTATING_EQUIPMENT","HOT_WORK","LOTO"]'::jsonb, true
FROM generate_series(2, 24) AS gs
ON CONFLICT (brigade_id, user_id) DO NOTHING;

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

INSERT INTO ppr_plans (id, created_at, updated_at, is_deleted, code, name, year, month, status, department_id, created_by_id, approved_by_id, notes)
VALUES
('00000000-0000-0000-0000-000000060002', now(), now(), false, 'DEMO-PPR-2026-06-UREA', 'Demo June 2026 PPR plan for urea workshop', 2026, 6, 'APPROVED', '00000000-0000-0000-0000-00000000d003', '00000000-0000-0000-0000-00000000a005', '00000000-0000-0000-0000-00000000a002', 'Approved urea monthly PPR plan'),
('00000000-0000-0000-0000-000000060003', now(), now(), false, 'DEMO-PPR-2026-06-HNO3', 'Demo June 2026 PPR plan for nitric acid workshop', 2026, 6, 'IN_PROGRESS', '00000000-0000-0000-0000-00000000d004', '00000000-0000-0000-0000-00000000a005', '00000000-0000-0000-0000-00000000a002', 'In-progress nitric acid PPR plan')
ON CONFLICT (code) DO NOTHING;

INSERT INTO ppr_tasks (id, created_at, updated_at, is_deleted, code, plan_id, regulation_id, equipment_id, title, scheduled_start, scheduled_end, due_date, status, priority, planned_labor_hours, actual_labor_hours, postpone_reason)
SELECT ('00000000-0000-0000-0000-' || '00000006' || lpad((100 + gs)::text, 4, '0'))::uuid, now(), now(), false,
       'DEMO-PPR-TASK-' || lpad(gs::text, 3, '0'),
       ('00000000-0000-0000-0000-' || '00000006000' || (1 + ((gs - 1) % 3))::text)::uuid,
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

-- 17.03. Repair requests, defects, work orders, material usage, labor, and executions.
INSERT INTO repair_requests (id, created_at, updated_at, is_deleted, number, title, description, equipment_id, department_id, location_id, reporter_id, assigned_to_id, priority, criticality, status, source, detected_at, target_completion_at, actual_completion_at, reacted_at, rejection_reason, clarification_reason, close_result)
SELECT ('00000000-0000-0000-0000-' || '00000007' || lpad(gs::text, 4, '0'))::uuid, now(), now(), false,
       'DEMO-RR-2026-' || lpad(gs::text, 4, '0'),
       req.title || ' ' || lpad(gs::text, 3, '0'), req.description,
       eq.id, eq.department_id, eq.location_id,
       (ARRAY['00000000-0000-0000-0000-00000000a003','00000000-0000-0000-0000-00000000a009','00000000-0000-0000-0000-00000000a011'])[1 + ((gs - 1) % 3)]::uuid,
       CASE WHEN gs % 6 IN (0, 1, 2, 3) THEN '00000000-0000-0000-0000-00000000a004'::uuid ELSE NULL END,
       (ARRAY['LOW','MEDIUM','HIGH','CRITICAL','EMERGENCY'])[1 + ((gs - 1) % 5)],
       (ARRAY['LOW','MEDIUM','HIGH','CRITICAL'])[1 + ((gs - 1) % 4)],
       (ARRAY['OPEN','NEEDS_CLARIFICATION','APPROVED','ASSIGNED','REJECTED','CLOSED'])[1 + ((gs - 1) % 6)],
       (ARRAY['MANUAL','OPERATOR','INSPECTION','MOBILE'])[1 + ((gs - 1) % 4)],
       now() - ((gs % 20) || ' days')::interval,
       now() + ((1 + (gs % 10)) || ' days')::interval,
       CASE WHEN gs % 6 = 5 THEN now() - '1 day'::interval ELSE NULL END,
       CASE WHEN gs % 6 IN (2, 3, 5) THEN now() - ((gs % 5) || ' days')::interval ELSE NULL END,
       CASE WHEN gs % 6 = 4 THEN 'Duplicate request after operator review' ELSE NULL END,
       CASE WHEN gs % 6 = 1 THEN 'Clarify operating mode and vibration trend' ELSE NULL END,
       CASE WHEN gs % 6 = 5 THEN 'Closed after inspection and test run' ELSE NULL END
FROM generate_series(3, 40) AS gs
JOIN LATERAL (
    SELECT e.id, e.department_id, e.location_id
    FROM equipment e
    WHERE e.id = ('00000000-0000-0000-0000-' || '00000001' || lpad((1 + ((gs - 1) % 60))::text, 4, '0'))::uuid
) AS eq ON true
CROSS JOIN LATERAL (
    SELECT (ARRAY['Vibration increase on rotating equipment','Seal leakage detected during shift walkdown','High bearing temperature alarm','Control valve slow response','Heat exchanger pressure drop increase','Motor insulation warning'])[1 + ((gs - 1) % 6)] AS title,
           (ARRAY['Operator found abnormal vibration during shift inspection.','Visible leak requires maintenance planning and spare part check.','Temperature trend exceeds normal operating band.','Valve positioner response requires calibration and mechanical inspection.','Pressure drop indicates fouling and cleaning need.','Electrical test trend requires insulation diagnostics.'])[1 + ((gs - 1) % 6)] AS description
) AS req
ON CONFLICT (number) DO NOTHING;

INSERT INTO defects (id, created_at, updated_at, is_deleted, code, title, description, equipment_id, repair_request_id, category, severity, failure_reason, root_cause, status, detected_at, resolved_at, recurrence_count)
SELECT ('00000000-0000-0000-0000-' || '00000009' || lpad(gs::text, 4, '0'))::uuid, now(), now(), false,
       'DEMO-DEF-2026-' || lpad(gs::text, 4, '0'),
       (ARRAY['Bearing wear','Seal face damage','Tube fouling','Valve seat erosion','Cable insulation defect','Sensor drift'])[1 + ((gs - 1) % 6)] || ' ' || lpad(gs::text, 3, '0'),
       'Production-like defect captured from inspection, repair request, or maintenance analysis.',
       ('00000000-0000-0000-0000-' || '00000001' || lpad((1 + ((gs - 1) % 60))::text, 4, '0'))::uuid,
       CASE WHEN gs <= 30 THEN ('00000000-0000-0000-0000-' || '00000007' || lpad((1 + ((gs - 1) % 40))::text, 4, '0'))::uuid ELSE NULL END,
       'DEMO-BEARING_WEAR', 'DEMO-MAJOR', 'DEMO-MECH_WEAR', 'DEMO-LUBRICATION',
       (ARRAY['OPEN','IN_ANALYSIS','IN_PROGRESS','RESOLVED','CLOSED'])[1 + ((gs - 1) % 5)],
       now() - ((gs % 30) || ' days')::interval,
       CASE WHEN gs % 5 IN (3, 4) THEN now() - ((gs % 10) || ' days')::interval ELSE NULL END,
       gs % 4
FROM generate_series(2, 30) AS gs
ON CONFLICT (code) DO NOTHING;

INSERT INTO work_orders (id, created_at, updated_at, is_deleted, number, title, equipment_id, department_id, repair_request_id, defect_id, ppr_task_id, contractor_id, warehouse_id, replacement_equipment_id, status, type, work_type, priority, start_planned_at, end_planned_at, started_at, completed_at, summary, result, closure_notes, created_by_id, approved_by_id)
SELECT ('00000000-0000-0000-0000-' || '00000008' || lpad(gs::text, 4, '0'))::uuid, now(), now(), false,
       'DEMO-WO-2026-' || lpad(gs::text, 4, '0'),
       wo.title || ' ' || lpad(gs::text, 3, '0'),
       eq.id, eq.department_id,
       CASE WHEN gs BETWEEN 3 AND 24 THEN ('00000000-0000-0000-0000-' || '00000007' || lpad((1 + ((gs - 1) % 40))::text, 4, '0'))::uuid ELSE NULL END,
       CASE WHEN gs BETWEEN 25 AND 40 THEN ('00000000-0000-0000-0000-' || '00000009' || lpad((1 + ((gs - 1) % 30))::text, 4, '0'))::uuid ELSE NULL END,
       CASE WHEN gs > 40 THEN ('00000000-0000-0000-0000-' || '00000006' || lpad((100 + (1 + ((gs - 1) % 75)))::text, 4, '0'))::uuid ELSE NULL END,
       NULL,
       CASE WHEN gs IN (10, 20, 30) THEN '00000000-0000-0000-0000-000000030003'::uuid WHEN gs % 4 = 0 THEN '00000000-0000-0000-0000-000000030001'::uuid ELSE NULL END,
       CASE WHEN gs IN (10, 20, 30) THEN ('00000000-0000-0000-0000-' || '00000001' || lpad((50 + (gs / 10))::text, 4, '0'))::uuid ELSE NULL END,
       (ARRAY['APPROVED','IN_PROGRESS','COMPLETED','CLOSED'])[1 + ((gs - 1) % 4)],
       CASE WHEN gs > 40 THEN 'PLANNED' WHEN gs BETWEEN 25 AND 40 THEN 'DEFECT' ELSE 'EMERGENCY' END,
       CASE WHEN gs IN (10, 20, 30) THEN 'REPLACEMENT' WHEN gs % 5 = 0 THEN 'DIAGNOSTICS' ELSE 'REPAIR' END,
       (ARRAY['MEDIUM','HIGH','CRITICAL','LOW'])[1 + ((gs - 1) % 4)],
       now() - ((gs % 8) || ' days')::interval,
       now() + ((1 + (gs % 8)) || ' days')::interval,
       CASE WHEN gs % 4 IN (1, 2, 3) THEN now() - ((gs % 5) || ' days')::interval ELSE NULL END,
       CASE WHEN gs % 4 IN (2, 3) THEN now() - ((gs % 3) || ' days')::interval ELSE NULL END,
       'Demo production-like work order generated from repair, defect, or PPR flow',
       CASE WHEN gs % 4 IN (2, 3) THEN 'Work completed and test run accepted' ELSE NULL END,
       CASE WHEN gs % 4 = 3 THEN 'Closed by workshop head after acceptance' ELSE NULL END,
       '00000000-0000-0000-0000-00000000a003'::uuid,
       '00000000-0000-0000-0000-00000000a002'::uuid
FROM generate_series(3, 50) AS gs
JOIN LATERAL (
    SELECT e.id, e.department_id
    FROM equipment e
    WHERE e.id = ('00000000-0000-0000-0000-' || '00000001' || lpad((1 + ((gs - 1) % 60))::text, 4, '0'))::uuid
) AS eq ON true
CROSS JOIN LATERAL (
    SELECT (ARRAY['Repair rotating equipment','Replace worn assembly','Inspect abnormal vibration','Clean heat transfer surface','Calibrate control loop','Electrical diagnostic work'])[1 + ((gs - 1) % 6)] AS title
) AS wo
ON CONFLICT (number) DO NOTHING;

INSERT INTO work_order_tasks (id, created_at, updated_at, is_deleted, work_order_id, title, description, status, assigned_to_id, planned_hours, actual_hours, started_at, completed_at)
SELECT ('00000000-0000-0000-0000-' || '000000081' || lpad(gs::text, 3, '0'))::uuid, now(), now(), false,
       ('00000000-0000-0000-0000-' || '00000008' || lpad((1 + ((gs - 1) % 50))::text, 4, '0'))::uuid,
       (ARRAY['Isolate equipment','Inspect assembly','Replace parts','Test run','Close work area'])[1 + ((gs - 1) % 5)],
       'Demo work order task for maintenance execution',
       (ARRAY['TODO','IN_PROGRESS','DONE','DONE'])[1 + ((gs - 1) % 4)],
       '00000000-0000-0000-0000-00000000a004'::uuid,
       (ARRAY[1.0,2.0,3.0,4.0])[1 + ((gs - 1) % 4)],
       CASE WHEN gs % 4 IN (2, 3) THEN (ARRAY[1.0,2.0,3.0,4.0])[1 + ((gs - 1) % 4)] ELSE NULL END,
       CASE WHEN gs % 4 IN (1, 2, 3) THEN now() - ((gs % 5) || ' days')::interval ELSE NULL END,
       CASE WHEN gs % 4 IN (2, 3) THEN now() - ((gs % 3) || ' days')::interval ELSE NULL END
FROM generate_series(3, 100) AS gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO repair_material_usages (id, created_at, updated_at, is_deleted, work_order_id, warehouse_id, spare_part_id, quantity, unit_cost)
SELECT ('00000000-0000-0000-0000-' || '000000083' || lpad(gs::text, 3, '0'))::uuid, now(), now(), false,
       ('00000000-0000-0000-0000-' || '00000008' || lpad((1 + ((gs - 1) % 50))::text, 4, '0'))::uuid,
       ('00000000-0000-0000-0000-' || '00000003000' || (1 + ((gs - 1) % 3))::text)::uuid,
       ('00000000-0000-0000-0000-' || '00000004' || lpad((1 + ((gs - 1) % 50))::text, 4, '0'))::uuid,
       1 + (gs % 5), 75000 + (gs * 3500)
FROM generate_series(1, 45) AS gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO labor_entries (id, created_at, updated_at, is_deleted, work_order_id, user_id, contractor_name, work_date, hours, rate, description)
SELECT ('00000000-0000-0000-0000-' || '000000084' || lpad(gs::text, 3, '0'))::uuid, now(), now(), false,
       ('00000000-0000-0000-0000-' || '00000008' || lpad((1 + ((gs - 1) % 50))::text, 4, '0'))::uuid,
       '00000000-0000-0000-0000-00000000a004'::uuid, NULL, DATE '2026-05-01' + (gs % 25),
       2 + (gs % 7), 85000, 'Demo labor entry for work execution'
FROM generate_series(1, 50) AS gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO work_executions (id, created_at, updated_at, is_deleted, work_order_id, performer_id, notes, started_at, ended_at, result)
SELECT ('00000000-0000-0000-0000-' || '000000085' || lpad(gs::text, 3, '0'))::uuid, now(), now(), false,
       ('00000000-0000-0000-0000-' || '00000008' || lpad((1 + ((gs - 1) % 50))::text, 4, '0'))::uuid,
       '00000000-0000-0000-0000-00000000a004'::uuid, 'Demo execution log',
       now() - ((gs % 10) || ' days')::interval,
       CASE WHEN gs % 3 <> 0 THEN now() - ((gs % 9) || ' days')::interval ELSE NULL END,
       CASE WHEN gs % 3 <> 0 THEN 'Accepted after test run' ELSE NULL END
FROM generate_series(1, 40) AS gs
ON CONFLICT (id) DO NOTHING;

-- 17.04. Defect lists and defect list lines.
INSERT INTO defect_lists (id, created_at, updated_at, is_deleted, code, title, equipment_id, repair_request_id, work_order_id, created_by_id, approved_by_id, status, total_labor_hours, total_estimated_cost, notes)
SELECT ('00000000-0000-0000-0000-' || '00000009' || lpad((100 + gs)::text, 4, '0'))::uuid, now(), now(), false,
       'DEMO-DL-2026-' || lpad(gs::text, 4, '0'),
       (ARRAY['Rotating equipment defect scope','Static equipment repair scope','Instrumentation correction scope','Electrical reliability scope'])[1 + ((gs - 1) % 4)] || ' ' || lpad(gs::text, 3, '0'),
       ('00000000-0000-0000-0000-' || '00000001' || lpad((1 + ((gs - 1) % 60))::text, 4, '0'))::uuid,
       ('00000000-0000-0000-0000-' || '00000007' || lpad((1 + ((gs - 1) % 40))::text, 4, '0'))::uuid,
       ('00000000-0000-0000-0000-' || '00000008' || lpad((1 + ((gs - 1) % 50))::text, 4, '0'))::uuid,
       '00000000-0000-0000-0000-00000000a009'::uuid,
       CASE WHEN gs % 3 <> 0 THEN '00000000-0000-0000-0000-00000000a002'::uuid ELSE NULL END,
       (ARRAY['DRAFT','APPROVED','APPROVED','CLOSED'])[1 + ((gs - 1) % 4)],
       4 + (gs % 12), 750000 + (gs * 185000),
       'Production-like demo defect list for workshop planning'
FROM generate_series(2, 10) AS gs
ON CONFLICT (code) DO NOTHING;

INSERT INTO defect_list_lines (id, created_at, updated_at, is_deleted, defect_list_id, defect_id, description, work_scope, material_specification, spare_part_id, required_quantity, estimated_labor_hours, estimated_cost)
SELECT ('00000000-0000-0000-0000-' || '00000009' || lpad((200 + gs)::text, 4, '0'))::uuid, now(), now(), false,
       ('00000000-0000-0000-0000-' || '00000009' || lpad((100 + (1 + ((gs - 1) % 10)))::text, 4, '0'))::uuid,
       ('00000000-0000-0000-0000-' || '00000009' || lpad((1 + ((gs - 1) % 30))::text, 4, '0'))::uuid,
       (ARRAY['Replace worn component','Inspect bearing housing','Clean and test assembly','Calibrate control element','Verify electrical terminal box'])[1 + ((gs - 1) % 5)],
       'Detailed maintenance scope for defect list line ' || lpad(gs::text, 3, '0'),
       'Use approved warehouse spare part or inspected equivalent',
       ('00000000-0000-0000-0000-' || '00000004' || lpad((1 + ((gs - 1) % 50))::text, 4, '0'))::uuid,
       1 + (gs % 6), 1 + (gs % 8), 180000 + (gs * 45000)
FROM generate_series(2, 40) AS gs
ON CONFLICT (id) DO NOTHING;

-- 17.05. Procurement, budgets, and actual costs.
INSERT INTO procurement_requests (id, created_at, updated_at, is_deleted, number, title, description, department_id, warehouse_id, requested_by, approved_by, status, source, required_by, total_estimated_cost, submitted_at, approved_at, ordered_at, received_at, rejection_reason)
SELECT ('00000000-0000-0000-0000-' || '0000000a' || lpad(gs::text, 4, '0'))::uuid, now(), now(), false,
       'DEMO-PR-2026-' || lpad(gs::text, 4, '0'),
       (ARRAY['Replenish critical rotating equipment spares','Purchase inspection consumables','Order planned outage materials','Emergency stock refill','Instrumentation spare parts request'])[1 + ((gs - 1) % 5)],
       'Production-like procurement request for demo warehouse and maintenance flows.',
       ('00000000-0000-0000-0000-' || '00000000d00' || (1 + ((gs - 1) % 5))::text)::uuid,
       ('00000000-0000-0000-0000-' || '00000003000' || (1 + ((gs - 1) % 3))::text)::uuid,
       '00000000-0000-0000-0000-00000000a007'::uuid,
       CASE WHEN gs % 6 IN (2, 3, 4) THEN '00000000-0000-0000-0000-00000000a002'::uuid ELSE NULL END,
       (ARRAY['DRAFT','SUBMITTED','APPROVED','ORDERED','RECEIVED','CANCELLED'])[1 + ((gs - 1) % 6)],
       CASE WHEN gs % 4 = 0 THEN 'AUTO' ELSE 'MANUAL' END,
       DATE '2026-06-01' + (gs % 20),
       1200000 + (gs * 275000),
       CASE WHEN gs % 6 <> 0 THEN now() - ((gs % 7) || ' days')::interval ELSE NULL END,
       CASE WHEN gs % 6 IN (2, 3, 4) THEN now() - ((gs % 5) || ' days')::interval ELSE NULL END,
       CASE WHEN gs % 6 IN (3, 4) THEN now() - ((gs % 4) || ' days')::interval ELSE NULL END,
       CASE WHEN gs % 6 = 4 THEN now() - ((gs % 3) || ' days')::interval ELSE NULL END,
       CASE WHEN gs % 6 = 5 THEN 'Cancelled after duplicate warehouse request review' ELSE NULL END
FROM generate_series(2, 12) AS gs
ON CONFLICT (number) DO NOTHING;

INSERT INTO procurement_request_lines (id, created_at, updated_at, is_deleted, request_id, spare_part_id, quantity, unit, unit_price, estimated_cost, notes)
SELECT ('00000000-0000-0000-0000-' || '0000000a' || lpad((100 + gs)::text, 4, '0'))::uuid, now(), now(), false,
       ('00000000-0000-0000-0000-' || '0000000a' || lpad((1 + ((gs - 1) % 12))::text, 4, '0'))::uuid,
       ('00000000-0000-0000-0000-' || '00000004' || lpad((1 + ((gs - 1) % 60))::text, 4, '0'))::uuid,
       (ARRAY['pc','set','l','kg'])[1 + ((gs - 1) % 4)],
       90000 + (gs * 12500),
       (2 + (gs % 10)) * (90000 + (gs * 12500)),
       'Demo procurement line linked to warehouse stock planning'
FROM generate_series(2, 36) AS gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO stock_movements (id, created_at, updated_at, is_deleted, warehouse_id, spare_part_id, work_order_id, type, quantity, unit_cost, document_number, created_by_id, occurred_at, notes)
SELECT ('00000000-0000-0000-0000-' || '000000052' || lpad(row_number() OVER (ORDER BY pr.number, prl.id)::text, 3, '0'))::uuid,
       now(), now(), false,
       pr.warehouse_id, prl.spare_part_id, NULL, 'RECEIPT', prl.quantity, prl.unit_price,
       'DEMO-SM-RECEIPT-' || pr.number || '-' || row_number() OVER (PARTITION BY pr.id ORDER BY prl.id),
       '00000000-0000-0000-0000-00000000a007'::uuid,
       COALESCE(pr.received_at, now() - interval '1 day'),
       'Receipt movement for received demo procurement request'
FROM procurement_requests pr
JOIN procurement_request_lines prl ON prl.request_id = pr.id AND prl.is_deleted = false
WHERE pr.number LIKE 'DEMO-PR-2026-%'
  AND pr.status = 'RECEIVED'
  AND pr.warehouse_id IS NOT NULL
  AND pr.is_deleted = false
ON CONFLICT (id) DO NOTHING;

INSERT INTO stock_movements (id, created_at, updated_at, is_deleted, warehouse_id, spare_part_id, work_order_id, type, quantity, unit_cost, document_number, created_by_id, occurred_at, notes)
SELECT ('00000000-0000-0000-0000-' || '000000053' || lpad(gs::text, 3, '0'))::uuid, now(), now(), false,
       ('00000000-0000-0000-0000-' || '00000003000' || (1 + ((gs - 1) % 3))::text)::uuid,
       ('00000000-0000-0000-0000-' || '00000004' || lpad((1 + ((gs - 1) % 50))::text, 4, '0'))::uuid,
       CASE WHEN gs % 5 IN (0, 1) THEN ('00000000-0000-0000-0000-' || '00000008' || lpad((1 + ((gs - 1) % 50))::text, 4, '0'))::uuid ELSE NULL END,
       (ARRAY['ISSUE','RESERVATION','RELEASE','ADJUSTMENT'])[1 + ((gs - 1) % 4)],
       CASE WHEN gs % 4 = 0 THEN 1 ELSE 1 + (gs % 5) END,
       65000 + (gs * 3000),
       'DEMO-SM-2026-' || lpad((100 + gs)::text, 4, '0'),
       '00000000-0000-0000-0000-00000000a006'::uuid,
       now() - ((gs % 30) || ' days')::interval,
       'Production-like demo stock movement history'
FROM generate_series(1, 60) AS gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO maintenance_budgets (id, created_at, updated_at, is_deleted, year, month, department_id, status, total_planned, total_actual)
VALUES
('00000000-0000-0000-0000-0000000b0002', now(), now(), false, 2026, 6, '00000000-0000-0000-0000-00000000d003', 'APPROVED', 72000000.00, 18800000.00),
('00000000-0000-0000-0000-0000000b0003', now(), now(), false, 2026, 6, '00000000-0000-0000-0000-00000000d004', 'DRAFT', 46000000.00, 6400000.00)
ON CONFLICT (id) DO NOTHING;

INSERT INTO budget_lines (id, created_at, updated_at, is_deleted, budget_id, cost_category_id, description, planned_amount, actual_amount)
SELECT ('00000000-0000-0000-0000-' || '0000000b' || lpad((100 + gs)::text, 4, '0'))::uuid, now(), now(), false,
       ('00000000-0000-0000-0000-' || '0000000b000' || (1 + ((gs - 1) % 3))::text)::uuid,
       ('00000000-0000-0000-0000-' || '00000000c10' || (1 + ((gs - 1) % 5))::text)::uuid,
       (ARRAY['Spare parts planned budget','Contractor repair reserve','Inspection material budget','Emergency repair reserve','PPR labor budget'])[1 + ((gs - 1) % 5)] || ' ' || lpad(gs::text, 3, '0'),
       1500000 + (gs * 420000), 200000 + (gs * 95000)
FROM generate_series(2, 18) AS gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO actual_costs (id, created_at, updated_at, is_deleted, work_order_id, repair_request_id, contractor_work_id, budget_line_id, cost_category_id, status, reviewed_by_id, reviewed_at, review_comment, amount, cost_date, notes)
SELECT ('00000000-0000-0000-0000-' || '0000000b' || lpad((200 + gs)::text, 4, '0'))::uuid, now(), now(), false,
       ('00000000-0000-0000-0000-' || '00000008' || lpad((1 + ((gs - 1) % 50))::text, 4, '0'))::uuid,
       CASE WHEN gs % 3 = 0 THEN ('00000000-0000-0000-0000-' || '00000007' || lpad((1 + ((gs - 1) % 40))::text, 4, '0'))::uuid ELSE NULL END,
       NULL,
       ('00000000-0000-0000-0000-' || '0000000b' || lpad((100 + (1 + ((gs - 1) % 18)))::text, 4, '0'))::uuid,
       ('00000000-0000-0000-0000-' || '00000000c10' || (1 + ((gs - 1) % 5))::text)::uuid,
       (ARRAY['PENDING','APPROVED','REJECTED','APPROVED'])[1 + ((gs - 1) % 4)],
       CASE WHEN gs % 4 <> 0 THEN '00000000-0000-0000-0000-00000000a008'::uuid ELSE NULL END,
       CASE WHEN gs % 4 <> 0 THEN now() - ((gs % 10) || ' days')::interval ELSE NULL END,
       CASE WHEN gs % 4 = 2 THEN 'Approved within demo finance route' WHEN gs % 4 = 3 THEN 'Rejected pending corrected invoice' ELSE NULL END,
       180000 + (gs * 65000),
       DATE '2026-05-01' + (gs % 25),
       'Production-like actual cost for demo maintenance accounting'
FROM generate_series(2, 30) AS gs
ON CONFLICT (id) DO NOTHING;

-- 17.06. Generic approvals.
INSERT INTO approval_requests (id, created_at, updated_at, is_deleted, document_type, document_id, title, requester_id, status, current_step, completed_at, description)
SELECT ('00000000-0000-0000-0000-' || '0000000c' || lpad(gs::text, 4, '0'))::uuid, now(), now(), false,
       (ARRAY['PROCUREMENT_REQUEST','WORK_ORDER','ACTUAL_COST','BUDGET'])[1 + ((gs - 1) % 4)],
       CASE ((gs - 1) % 4)
           WHEN 0 THEN ('00000000-0000-0000-0000-' || '0000000a' || lpad((1 + ((gs - 1) % 12))::text, 4, '0'))::uuid
           WHEN 1 THEN ('00000000-0000-0000-0000-' || '00000008' || lpad((1 + ((gs - 1) % 50))::text, 4, '0'))::uuid
           WHEN 2 THEN ('00000000-0000-0000-0000-' || '0000000b' || lpad((200 + (1 + ((gs - 1) % 30)))::text, 4, '0'))::uuid
           ELSE ('00000000-0000-0000-0000-' || '0000000b000' || (1 + ((gs - 1) % 3))::text)::uuid
       END,
       'Demo approval route ' || lpad(gs::text, 3, '0'),
       '00000000-0000-0000-0000-00000000a007'::uuid,
       (ARRAY['PENDING','APPROVED','REJECTED','PENDING'])[1 + ((gs - 1) % 4)],
       CASE WHEN gs % 4 IN (1, 0) THEN 1 ELSE 2 END,
       CASE WHEN gs % 4 IN (2, 3) THEN now() - ((gs % 6) || ' days')::interval ELSE NULL END,
       'Production-like approval for demo maintenance governance'
FROM generate_series(2, 12) AS gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO approval_steps (id, created_at, updated_at, is_deleted, request_id, step_number, approver_id, approver_role, decision, decided_at, comment)
SELECT ('00000000-0000-0000-0000-' || '0000000c' || lpad((300 + row_number() OVER (ORDER BY req.gs, step.step_number))::text, 4, '0'))::uuid,
       now(), now(), false,
       ('00000000-0000-0000-0000-' || '0000000c' || lpad(req.gs::text, 4, '0'))::uuid,
       step.step_number,
       CASE WHEN step.step_number = 1 THEN '00000000-0000-0000-0000-00000000a002'::uuid ELSE '00000000-0000-0000-0000-00000000a001'::uuid END,
       CASE WHEN step.step_number = 1 THEN 'CHIEF_MECHANIC' ELSE 'TECHNICAL_DIRECTOR' END,
       CASE
           WHEN req.gs % 4 = 1 AND step.step_number = 1 THEN 'PENDING'
           WHEN req.gs % 4 = 2 THEN 'APPROVED'
           WHEN req.gs % 4 = 3 AND step.step_number = 1 THEN 'REJECTED'
           WHEN req.gs % 4 = 0 AND step.step_number = 1 THEN 'APPROVED'
           ELSE 'PENDING'
       END,
       CASE
           WHEN req.gs % 4 IN (2, 3) OR (req.gs % 4 = 0 AND step.step_number = 1) THEN now() - ((req.gs % 5) || ' days')::interval
           ELSE NULL
       END,
       CASE WHEN req.gs % 4 = 3 AND step.step_number = 1 THEN 'Rejected in demo route for correction' ELSE NULL END
FROM generate_series(2, 12) AS req(gs)
CROSS JOIN (VALUES (1), (2)) AS step(step_number)
ON CONFLICT (request_id, step_number) DO NOTHING;

-- 17.07. Inspection routes, checkpoints, rounds, and results.
INSERT INTO inspection_routes (id, created_at, updated_at, is_deleted, code, name, department_id, frequency, target_duration_min, description, is_active)
SELECT ('00000000-0000-0000-0000-' || '0000000d' || lpad(gs::text, 4, '0'))::uuid, now(), now(), false,
       'DEMO-IR-' || (ARRAY['UREA-SHIFT','HNO3-SHIFT','MECH-DAILY','AMM-WEEKLY','UTIL-DAILY'])[gs - 1],
       (ARRAY['Demo urea shift inspection','Demo nitric acid shift inspection','Demo mechanical daily inspection','Demo ammonia weekly reliability route','Demo utilities daily inspection'])[gs - 1],
       ('00000000-0000-0000-0000-' || '00000000d00' || (1 + ((gs - 1) % 5))::text)::uuid,
       (ARRAY['SHIFT','SHIFT','DAILY','WEEKLY','DAILY'])[gs - 1],
       (ARRAY[45,50,60,90,40])[gs - 1],
       'Production-like demo inspection route',
       true
FROM generate_series(2, 6) AS gs
ON CONFLICT (code) DO NOTHING;

INSERT INTO inspection_checkpoints (id, created_at, updated_at, is_deleted, route_id, order_index, equipment_id, location_id, title, instruction, check_type, expected_min, expected_max, expected_unit, is_mandatory)
SELECT ('00000000-0000-0000-0000-' || '0000000d' || lpad((100 + gs)::text, 4, '0'))::uuid, now(), now(), false,
       ('00000000-0000-0000-0000-' || '0000000d000' || (1 + ((gs - 1) % 6))::text)::uuid,
       1 + ((gs - 1) % 8),
       ('00000000-0000-0000-0000-' || '00000001' || lpad((1 + ((gs - 1) % 60))::text, 4, '0'))::uuid,
       ('00000000-0000-0000-0000-' || '00000000a' || lpad((101 + ((gs - 1) % 12))::text, 3, '0'))::uuid,
       (ARRAY['Check vibration','Check leakage','Check bearing temperature','Check pressure indication','Check motor sound','Check valve position'])[1 + ((gs - 1) % 6)] || ' ' || lpad(gs::text, 3, '0'),
       'Record observation and measurement according to route instruction',
       (ARRAY['MEASUREMENT','VISUAL','MEASUREMENT','MEASUREMENT','VISUAL','BOOLEAN'])[1 + ((gs - 1) % 6)],
       CASE WHEN gs % 6 IN (1, 3, 4) THEN 0 ELSE NULL END,
       CASE WHEN gs % 6 = 1 THEN 4.5 WHEN gs % 6 = 3 THEN 75 WHEN gs % 6 = 4 THEN 10 ELSE NULL END,
       CASE WHEN gs % 6 = 1 THEN 'mm/s' WHEN gs % 6 = 3 THEN 'C' WHEN gs % 6 = 4 THEN 'bar' ELSE NULL END,
       true
FROM generate_series(3, 36) AS gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO inspection_rounds (id, created_at, updated_at, is_deleted, route_id, performed_by, started_at, completed_at, status, findings_count, alarm_count, notes)
SELECT ('00000000-0000-0000-0000-' || '0000000d' || lpad((200 + gs)::text, 4, '0'))::uuid, now(), now(), false,
       ('00000000-0000-0000-0000-' || '0000000d000' || (1 + ((gs - 1) % 6))::text)::uuid,
       '00000000-0000-0000-0000-00000000a004'::uuid,
       now() - ((gs % 18) || ' days')::interval,
       CASE WHEN gs % 5 <> 0 THEN now() - ((gs % 18) || ' days')::interval + interval '45 minutes' ELSE NULL END,
       CASE WHEN gs % 5 = 0 THEN 'IN_PROGRESS' ELSE 'COMPLETED' END,
       CASE WHEN gs % 4 = 0 THEN 2 ELSE gs % 2 END,
       CASE WHEN gs % 7 = 0 THEN 1 ELSE 0 END,
       'Production-like inspection round for demo route'
FROM generate_series(2, 24) AS gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO inspection_round_results (id, created_at, updated_at, is_deleted, round_id, checkpoint_id, status, measured_value, measured_unit, comment, defect_id, photo_file_ids)
SELECT ('00000000-0000-0000-0000-' || '0000000d' || lpad((300 + gs)::text, 4, '0'))::uuid, now(), now(), false,
       ('00000000-0000-0000-0000-' || '0000000d' || lpad((200 + (1 + ((gs - 1) % 24)))::text, 4, '0'))::uuid,
       ('00000000-0000-0000-0000-' || '0000000d' || lpad((100 + (1 + ((gs - 1) % 36)))::text, 4, '0'))::uuid,
       (ARRAY['OK','OK','WARN','FAIL'])[1 + ((gs - 1) % 4)],
       CASE WHEN gs % 4 IN (1, 2, 3) THEN round((2.0 + (gs::numeric / 30)), 2) ELSE NULL END,
       CASE WHEN gs % 4 IN (1, 2, 3) THEN (ARRAY['mm/s','C','bar'])[1 + ((gs - 1) % 3)] ELSE NULL END,
       CASE WHEN gs % 4 = 3 THEN 'Warning threshold exceeded, monitor trend' WHEN gs % 4 = 0 THEN 'Failed check, defect linked' ELSE 'Within expected range' END,
       CASE WHEN gs % 4 = 0 THEN ('00000000-0000-0000-0000-' || '00000009' || lpad((1 + ((gs - 1) % 30))::text, 4, '0'))::uuid ELSE NULL END,
       '[]'::jsonb
FROM generate_series(3, 120) AS gs
ON CONFLICT (id) DO NOTHING;

-- 17.08. Knowledge base.
INSERT INTO knowledge_articles (id, created_at, updated_at, is_deleted, code, title, kind, equipment_type_id, equipment_id, defect_id, work_order_id, problem, root_cause, solution, preventive_actions, tags, author_id, view_count)
SELECT ('00000000-0000-0000-0000-' || '0000000e' || lpad(gs::text, 4, '0'))::uuid, now(), now(), false,
       'DEMO-KB-' || (ARRAY['CMP','PMP','RCT','HEX','VALVE','MOTOR','INST','PPR'])[1 + ((gs - 1) % 8)] || '-' || lpad(gs::text, 3, '0'),
       (ARRAY['Compressor vibration troubleshooting','Pump seal replacement procedure','Reactor agitator inspection lesson','Heat exchanger cleaning procedure','Control valve calibration lesson','Motor insulation diagnostic note','Pressure transmitter drift response','Monthly PPR preparation checklist'])[1 + ((gs - 1) % 8)] || ' ' || lpad(gs::text, 3, '0'),
       (ARRAY['TROUBLESHOOTING','PROCEDURE','LESSON_LEARNED','PROCEDURE'])[1 + ((gs - 1) % 4)],
       ('00000000-0000-0000-0000-' || '00000000f00' || (1 + ((gs - 1) % 8))::text)::uuid,
       ('00000000-0000-0000-0000-' || '00000001' || lpad((1 + ((gs - 1) % 60))::text, 4, '0'))::uuid,
       CASE WHEN gs % 3 = 0 THEN ('00000000-0000-0000-0000-' || '00000009' || lpad((1 + ((gs - 1) % 30))::text, 4, '0'))::uuid ELSE NULL END,
       CASE WHEN gs % 4 = 0 THEN ('00000000-0000-0000-0000-' || '00000008' || lpad((1 + ((gs - 1) % 50))::text, 4, '0'))::uuid ELSE NULL END,
       'Observed maintenance issue from demo production-like operating history',
       'Root cause documented from inspection, repair request, or work order analysis',
       'Follow approved maintenance procedure and record verification results',
       'Add trend review, spare readiness check, and operator walkdown item',
       jsonb_build_array('demo', 'navoiyazot', lower((ARRAY['compressor','pump','reactor','heat-exchanger','valve','motor','instrument','ppr'])[1 + ((gs - 1) % 8)])),
       '00000000-0000-0000-0000-00000000a009'::uuid,
       5 + (gs * 3)
FROM generate_series(3, 18) AS gs
ON CONFLICT (code) DO NOTHING;
