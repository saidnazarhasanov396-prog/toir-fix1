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

-- 17.01.X Foundation supplements: manufacturers, service classes, certifications.
INSERT INTO manufacturers (id, created_at, updated_at, is_deleted, code, name, country, website, contact_info)
SELECT ('10000000-0000-0000-0000-' || '00000001' || lpad(gs::text, 4, '0'))::uuid,
       now(), now(), false,
       'MFG-DEMO-' || lpad(gs::text, 3, '0'),
       (ARRAY['Navoiy Machinery Plant','UzPump Systems','Asia Compressors','KIP Tech Instruments','Central Valve Works'])[1 + ((gs - 1) % 5)] || ' #' || lpad(gs::text, 3, '0'),
       (ARRAY['UZ','KZ','RU','DE','CN'])[1 + ((gs - 1) % 5)],
       'https://demo-mfg-' || lpad(gs::text, 3, '0') || '.local',
       'Demo manufacturer contact'
FROM generate_series(1, 15) AS gs
ON CONFLICT (code) DO NOTHING;

INSERT INTO service_classes (id, created_at, updated_at, is_deleted, code, name, name_en, name_uz, description)
VALUES
('10000000-0000-0000-0000-000000020001', now(), now(), false, 'SRV-A', 'Service class A', 'Service class A', 'A xizmat sinfi', 'Critical unit service class'),
('10000000-0000-0000-0000-000000020002', now(), now(), false, 'SRV-B', 'Service class B', 'Service class B', 'B xizmat sinfi', 'High-priority service class'),
('10000000-0000-0000-0000-000000020003', now(), now(), false, 'SRV-C', 'Service class C', 'Service class C', 'C xizmat sinfi', 'Normal service class'),
('10000000-0000-0000-0000-000000020004', now(), now(), false, 'SRV-D', 'Service class D', 'Service class D', 'D xizmat sinfi', 'Support service class'),
('10000000-0000-0000-0000-000000020005', now(), now(), false, 'SRV-E', 'Service class E', 'Service class E', 'E xizmat sinfi', 'Auxiliary service class')
ON CONFLICT (code) DO NOTHING;

INSERT INTO certification_types (id, created_at, updated_at, is_deleted, code, name, name_en, name_uz, category, validity_months, description)
VALUES
('10000000-0000-0000-0000-000000030001', now(), now(), false, 'CERT-MECH-L3', 'Mechanical specialist L3', 'Mechanical specialist L3', 'Mexanik mutaxassis L3', 'MECHANICAL', 36, 'Mechanical maintenance certification'),
('10000000-0000-0000-0000-000000030002', now(), now(), false, 'CERT-ELEC-V', 'Electrical safety group V', 'Electrical safety group V', 'Elektr xavfsizligi V', 'ELECTRICAL', 12, 'Electrical safety certification'),
('10000000-0000-0000-0000-000000030003', now(), now(), false, 'CERT-INSTR', 'Instrumentation specialist', 'Instrumentation specialist', 'KIPiA mutaxassisi', 'INSTRUMENT', 24, 'Instrumentation and controls certification'),
('10000000-0000-0000-0000-000000030004', now(), now(), false, 'CERT-LOTO', 'LOTO safety permit', 'LOTO safety permit', 'LOTO xavfsizlik ruxsati', 'SAFETY', 12, 'Lockout tagout training'),
('10000000-0000-0000-0000-000000030005', now(), now(), false, 'CERT-HOTWORK', 'Hot work permit training', 'Hot work permit training', 'Issiq ishlar tayyorligi', 'SAFETY', 12, 'Hot work safety training'),
('10000000-0000-0000-0000-000000030006', now(), now(), false, 'CERT-WELD', 'Industrial welding certificate', 'Industrial welding certificate', 'Sanoat payvandlash sertifikati', 'WELDING', 24, 'Welding qualification'),
('10000000-0000-0000-0000-000000030007', now(), now(), false, 'CERT-NDT', 'NDT inspector certificate', 'NDT inspector certificate', 'NDT inspektori sertifikati', 'QUALITY', 36, 'Non-destructive testing'),
('10000000-0000-0000-0000-000000030008', now(), now(), false, 'CERT-PPR', 'PPR planning certificate', 'PPR planning certificate', 'PPR rejalashtirish sertifikati', 'PLANNING', 24, 'Preventive maintenance planning')
ON CONFLICT (code) DO NOTHING;

INSERT INTO user_certifications (id, created_at, updated_at, is_deleted, user_id, type_code, status, issued_at, expires_at, certificate_number, issued_by, grade_or_level, notes)
SELECT ('10000000-0000-0000-0000-' || '00000004' || lpad(gs::text, 4, '0'))::uuid,
       now(), now(), false,
       ('00000000-0000-0000-0000-' || '00000000a0' || lpad((1 + ((gs - 1) % 12))::text, 2, '0'))::uuid,
       (ARRAY['CERT-MECH-L3','CERT-ELEC-V','CERT-INSTR','CERT-LOTO','CERT-HOTWORK','CERT-WELD','CERT-NDT','CERT-PPR'])[1 + ((gs - 1) % 8)],
       (ARRAY['ACTIVE','ACTIVE','ACTIVE','EXPIRING'])[1 + ((gs - 1) % 4)],
       DATE '2025-01-01' + ((gs - 1) * 9),
       DATE '2027-01-01' + ((gs - 1) * 9),
       'UC-DEMO-' || lpad(gs::text, 4, '0'),
       'Demo Certification Center',
       (ARRAY['L2','L3','L4','V'])[1 + ((gs - 1) % 4)],
       'Demo certification for real-case maintenance staffing'
FROM generate_series(1, 20) AS gs
ON CONFLICT (id) DO NOTHING;

