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

