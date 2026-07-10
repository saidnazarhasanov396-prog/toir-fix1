WITH regulation_plan AS (
    SELECT *
    FROM (VALUES
        (1, 'NAV-MR-PUMP-MONTHLY', 'NAV-ET-PUMP', 'Nasos oylik profilaktik xizmati', 'Nasos agregatlari vibratsiya, moylash va zichlik holatini davriy nazorat qilish', 'PREVENTIVE', 6.0::double precision, 'MONTH', 1, 5, false, 'NAV-AZOT-AMM', 'nav.azot.mech.foreman', 'MEDIUM'),
        (2, 'NAV-MR-COMPRESSOR-QUARTER', 'NAV-ET-COMPRESSOR', 'Kompressor choraklik texnik xizmati', 'Kompressor moylash, sovitish va himoya zanjirlarini choraklik nazorat qilish', 'PREVENTIVE', 10.0::double precision, 'QUARTER', 1, 10, true, 'NAV-AZOT-AMM', 'nav.azot.mech.foreman', 'HIGH'),
        (3, 'NAV-MR-MOTOR-QUARTER', 'NAV-ET-MOTOR', 'Elektr dvigatel choraklik nazorati', 'Elektr dvigatel podshipnik, izolyatsiya va himoya rele holatini tekshirish', 'ELECTRICAL', 5.5::double precision, 'QUARTER', 1, 7, false, 'NAV-AZOT-ELEC', 'nav.azot.elec.foreman', 'MEDIUM'),
        (4, 'NAV-MR-INSTRUMENT-CALIBRATION', 'NAV-ET-INSTRUMENT-CABINET', 'NQOA kalibrovka va signal zanjiri xizmati', 'Boshqaruv shkafi signal zanjirlari va o''lchov konturlarini sozlash', 'INSTRUMENTATION', 6.5::double precision, 'QUARTER', 1, 7, false, 'NAV-AZOT-ELEC', 'nav.azot.elec.foreman', 'HIGH'),
        (5, 'NAV-MR-VEHICLE-TRUCK-MONTHLY', 'NAV-ET-VEHICLE-TRUCK', 'Yuk transporti oylik texnik korigi', 'Yuk transportlari tormoz, moylash va yurish qismini oylik nazorat qilish', 'INSPECTION', 4.0::double precision, 'MONTH', 1, 5, false, 'NAV-AZOT-TR', 'nav.azot.transport.foreman', 'MEDIUM'),
        (6, 'NAV-MR-VEHICLE-SPECIAL-MONTHLY', 'NAV-ET-VEHICLE-SPECIAL', 'Maxsus texnika oylik texnik korigi', 'Kran, yuklagich va servis texnikalari gidravlika hamda xavfsizlik uzellarini nazorat qilish', 'INSPECTION', 5.0::double precision, 'MONTH', 1, 5, false, 'NAV-AZOT-TR', 'nav.azot.transport.foreman', 'HIGH'),
        (7, 'NAV-MR-PIPELINE-ANNUAL', 'NAV-ET-PIPELINE', 'Quvur liniyasi yillik kapital ta''mir tayyorgarligi', 'Quvur liniyasi flanes, tayanch va korroziya holatini yillik baholash', 'OVERHAUL', 12.0::double precision, 'YEAR', 1, 20, true, 'NAV-AZOT-MEX', 'nav.azot.mech.foreman', 'HIGH'),
        (8, 'NAV-MR-GENERATOR-ANNUAL', 'NAV-ET-GENERATOR', 'Dizel generator yillik chuqur korigi', 'Zaxira generator moylash, elektr ulanish va yuklama rejimini yillik baholash', 'PREVENTIVE', 8.0::double precision, 'YEAR', 1, 15, false, 'NAV-AZOT-ELEC', 'nav.azot.elec.foreman', 'HIGH')
    ) AS v(idx, code, equipment_type_code, name, description, maintenance_kind, normative_labor_hours, periodicity_unit, periodicity_value, tolerance_days, requires_shutdown, department_code, responsible_username, default_priority)
)
INSERT INTO maintenance_regulations (
    id, created_at, updated_at, is_deleted,
    code, name, description, equipment_type_id, maintenance_kind,
    normative_labor_hours, is_active, periodicity_unit, periodicity_value,
    tolerance_days, requires_shutdown, trigger_policy, recalculation_policy,
    initial_schedule_policy, automation_action, approval_result_action,
    duplicate_policy, lead_time_days, default_department_id,
    default_responsible_id, default_priority, requires_approval
)
SELECT ('71000000-0000-0000-0000-00000010' || lpad(rp.idx::text, 4, '0'))::uuid,
       now(), now(), false,
       rp.code, rp.name, rp.description, et.id, rp.maintenance_kind,
       rp.normative_labor_hours, true, rp.periodicity_unit, rp.periodicity_value,
       rp.tolerance_days, rp.requires_shutdown, 'ANY', 'FROM_ACTUAL_COMPLETION',
       'FROM_OPERATION_START', 'REQUIRE_APPROVAL', 'CREATE_TASK',
       'ONE_ITEM_PER_CYCLE', rp.tolerance_days, d.id,
       u.id, rp.default_priority, true
FROM regulation_plan rp
JOIN equipment_types et ON et.code = rp.equipment_type_code AND et.is_deleted = false
JOIN departments d ON d.code = rp.department_code AND d.is_deleted = false
LEFT JOIN users u ON u.username = rp.responsible_username AND u.is_deleted = false
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    description = EXCLUDED.description,
    equipment_type_id = EXCLUDED.equipment_type_id,
    maintenance_kind = EXCLUDED.maintenance_kind,
    normative_labor_hours = EXCLUDED.normative_labor_hours,
    is_active = EXCLUDED.is_active,
    periodicity_unit = EXCLUDED.periodicity_unit,
    periodicity_value = EXCLUDED.periodicity_value,
    tolerance_days = EXCLUDED.tolerance_days,
    requires_shutdown = EXCLUDED.requires_shutdown,
    trigger_policy = EXCLUDED.trigger_policy,
    recalculation_policy = EXCLUDED.recalculation_policy,
    initial_schedule_policy = EXCLUDED.initial_schedule_policy,
    automation_action = EXCLUDED.automation_action,
    approval_result_action = EXCLUDED.approval_result_action,
    duplicate_policy = EXCLUDED.duplicate_policy,
    lead_time_days = EXCLUDED.lead_time_days,
    default_department_id = EXCLUDED.default_department_id,
    default_responsible_id = EXCLUDED.default_responsible_id,
    default_priority = EXCLUDED.default_priority,
    requires_approval = EXCLUDED.requires_approval,
    is_deleted = false,
    updated_at = now();

WITH actor AS (
    SELECT
        (SELECT id FROM users WHERE username = 'nav.azot.planner' AND is_deleted = false) AS planner_id,
        (SELECT id FROM users WHERE username = 'nav.azot.chief.mechanic' AND is_deleted = false) AS chief_mechanic_id
),
plan_values AS (
    SELECT *
    FROM (VALUES
        (1, 'NAV-PPR-2026-001', 'Nasos agregatlari oylik profilaktik ko''rigi', 'NAV-AZOT-AMM', 'APPROVED', DATE '2026-07-01', DATE '2026-07-31', 'CALENDAR', 'MONTHLY', 'Nasos agregatlari bo''yicha oylik profilaktika ishlari tasdiqlandi'),
        (2, 'NAV-PPR-2026-002', 'Kompressor uskunalari choraklik texnik xizmat rejasi', 'NAV-AZOT-AMM', 'APPROVED', DATE '2026-07-01', DATE '2026-09-30', 'CALENDAR', 'QUARTERLY', 'Kompressor zali choraklik xizmat ishlari kelishildi'),
        (3, 'NAV-PPR-2026-003', 'Elektr va NQO''A uskunalari kalibrovka rejasi', 'NAV-AZOT-ELEC', 'IN_PROGRESS', DATE '2026-07-01', DATE '2026-09-30', 'CALENDAR', 'QUARTERLY', 'Elektr va NQOA konturlari bo''yicha ishlar bajarilmoqda'),
        (4, 'NAV-PPR-2026-004', 'Transport vositalari oylik texnik ko''rik rejasi', 'NAV-AZOT-TR', 'APPROVED', DATE '2026-07-01', DATE '2026-07-31', 'CALENDAR', 'MONTHLY', 'Transport parki oylik ko''rik jadvali tasdiqlandi'),
        (5, 'NAV-PPR-2026-005', 'Yillik kapital ta''mirga tayyorgarlik rejasi', 'NAV-AZOT-MEX', 'GENERATED', DATE '2026-08-01', DATE '2026-12-31', 'ONE_TIME', NULL, 'Yillik kapital ta''mir oldi tayyorgarlik ishlari shakllantirildi')
    ) AS v(idx, code, name, department_code, status, start_date, end_date, schedule_type, frequency, notes)
)
INSERT INTO ppr_plans (
    id, created_at, updated_at, is_deleted, created_by_id, updated_by_id,
    code, name, start_date, end_date, status, department_id, approved_by_id,
    notes, ppr_type, schedule_type, frequency, interval_hours, scope_type
)
SELECT ('71000000-0000-0000-0000-00000011' || lpad(pv.idx::text, 4, '0'))::uuid,
       now(), now(), false, actor.planner_id, actor.planner_id,
       pv.code, pv.name, pv.start_date, pv.end_date, pv.status, d.id,
       CASE WHEN pv.status IN ('APPROVED', 'IN_PROGRESS') THEN actor.chief_mechanic_id ELSE NULL END,
       pv.notes, 'PREVENTIVE_MAINTENANCE', pv.schedule_type, pv.frequency, NULL::bigint, 'ENTERPRISE'
FROM plan_values pv
JOIN departments d ON d.code = pv.department_code AND d.is_deleted = false
CROSS JOIN actor
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    start_date = EXCLUDED.start_date,
    end_date = EXCLUDED.end_date,
    status = EXCLUDED.status,
    department_id = EXCLUDED.department_id,
    approved_by_id = EXCLUDED.approved_by_id,
    notes = EXCLUDED.notes,
    ppr_type = EXCLUDED.ppr_type,
    schedule_type = EXCLUDED.schedule_type,
    frequency = EXCLUDED.frequency,
    interval_hours = EXCLUDED.interval_hours,
    scope_type = EXCLUDED.scope_type,
    is_deleted = false,
    updated_by_id = EXCLUDED.updated_by_id,
    updated_at = now();

WITH target_values AS (
    SELECT *
    FROM (VALUES
        (1, 'NAV-PPR-2026-001', 'NAV-MR-PUMP-MONTHLY'),
        (2, 'NAV-PPR-2026-002', 'NAV-MR-COMPRESSOR-QUARTER'),
        (3, 'NAV-PPR-2026-003', 'NAV-MR-MOTOR-QUARTER'),
        (4, 'NAV-PPR-2026-003', 'NAV-MR-INSTRUMENT-CALIBRATION'),
        (5, 'NAV-PPR-2026-003', 'NAV-MR-GENERATOR-ANNUAL'),
        (6, 'NAV-PPR-2026-004', 'NAV-MR-VEHICLE-TRUCK-MONTHLY'),
        (7, 'NAV-PPR-2026-004', 'NAV-MR-VEHICLE-SPECIAL-MONTHLY'),
        (8, 'NAV-PPR-2026-005', 'NAV-MR-PIPELINE-ANNUAL'),
        (9, 'NAV-PPR-2026-005', 'NAV-MR-GENERATOR-ANNUAL')
    ) AS v(idx, plan_code, regulation_code)
)
INSERT INTO ppr_plan_targets (
    id, created_at, updated_at, is_deleted,
    plan_id, target_type, equipment_id, equipment_type_id, regulation_id
)
SELECT ('71000000-0000-0000-0000-00000012' || lpad(tv.idx::text, 4, '0'))::uuid,
       now(), now(), false,
       p.id, 'REGULATION', NULL, NULL, mr.id
FROM target_values tv
JOIN ppr_plans p ON p.code = tv.plan_code AND p.is_deleted = false
JOIN maintenance_regulations mr ON mr.code = tv.regulation_code AND mr.is_deleted = false
ON CONFLICT (plan_id, regulation_id) WHERE is_deleted = false AND target_type = 'REGULATION' AND regulation_id IS NOT NULL DO UPDATE
SET target_type = EXCLUDED.target_type,
    equipment_id = EXCLUDED.equipment_id,
    equipment_type_id = EXCLUDED.equipment_type_id,
    is_deleted = false,
    updated_at = now();

WITH task_values AS (
    SELECT *
    FROM (VALUES
        (1, 'NAV-PPR-2026-001', 'NAV-MR-PUMP-MONTHLY', 'NAV-EQ-PUMP-001', 'Nasos korpusini tashqi ko''rikdan o''tkazish', TIMESTAMP '2026-07-10 08:00:00', TIMESTAMP '2026-07-10 10:00:00', TIMESTAMP '2026-07-10 18:00:00', 'PLANNED', 'MEDIUM', 2.0::double precision, NULL::double precision),
        (2, 'NAV-PPR-2026-001', 'NAV-MR-PUMP-MONTHLY', 'NAV-EQ-PUMP-001', 'Podshipnik haroratini nazorat qilish', TIMESTAMP '2026-07-11 08:00:00', TIMESTAMP '2026-07-11 11:00:00', TIMESTAMP '2026-07-11 18:00:00', 'APPROVED', 'MEDIUM', 3.0::double precision, NULL::double precision),
        (3, 'NAV-PPR-2026-001', 'NAV-MR-PUMP-MONTHLY', 'NAV-EQ-PUMP-002', 'Vibrodiagnostika o''lchovlarini bajarish', TIMESTAMP '2026-07-12 09:00:00', TIMESTAMP '2026-07-12 12:00:00', TIMESTAMP '2026-07-12 18:00:00', 'IN_PROGRESS', 'HIGH', 3.0::double precision, NULL::double precision),
        (4, 'NAV-PPR-2026-001', 'NAV-MR-PUMP-MONTHLY', 'NAV-EQ-PUMP-001', 'Moylash tizimi holatini tekshirish', TIMESTAMP '2026-07-06 08:00:00', TIMESTAMP '2026-07-06 11:30:00', TIMESTAMP '2026-07-06 18:00:00', 'COMPLETED', 'MEDIUM', 3.5::double precision, 3.25::double precision),
        (5, 'NAV-PPR-2026-001', 'NAV-MR-PUMP-MONTHLY', 'NAV-EQ-PUMP-002', 'Salnik uzelini ko''rikdan o''tkazish', TIMESTAMP '2026-07-15 08:00:00', TIMESTAMP '2026-07-15 12:00:00', TIMESTAMP '2026-07-15 18:00:00', 'PLANNED', 'HIGH', 4.0::double precision, NULL::double precision),
        (6, 'NAV-PPR-2026-001', 'NAV-MR-PUMP-MONTHLY', 'NAV-EQ-PUMP-001', 'Flanes birikmalarini nazorat qilish', TIMESTAMP '2026-07-16 08:30:00', TIMESTAMP '2026-07-16 11:30:00', TIMESTAMP '2026-07-16 18:00:00', 'APPROVED', 'MEDIUM', 3.0::double precision, NULL::double precision),
        (7, 'NAV-PPR-2026-001', 'NAV-MR-PUMP-MONTHLY', 'NAV-EQ-PUMP-002', 'Zaxira nasos ishga tushirish sinovini bajarish', TIMESTAMP '2026-07-18 09:00:00', TIMESTAMP '2026-07-18 13:00:00', TIMESTAMP '2026-07-18 18:00:00', 'PLANNED', 'MEDIUM', 4.0::double precision, NULL::double precision),
        (8, 'NAV-PPR-2026-001', 'NAV-MR-PUMP-MONTHLY', 'NAV-EQ-PUMP-002', 'Nasos agregati ish rejimini qayd qilish', TIMESTAMP '2026-07-08 08:00:00', TIMESTAMP '2026-07-08 10:30:00', TIMESTAMP '2026-07-08 18:00:00', 'OVERDUE', 'HIGH', 2.5::double precision, NULL::double precision),
        (9, 'NAV-PPR-2026-002', 'NAV-MR-COMPRESSOR-QUARTER', 'NAV-EQ-COMP-001', 'Kompressor moylash tizimini tekshirish', TIMESTAMP '2026-07-21 08:00:00', TIMESTAMP '2026-07-21 13:00:00', TIMESTAMP '2026-07-21 18:00:00', 'PLANNED', 'HIGH', 5.0::double precision, NULL::double precision),
        (10, 'NAV-PPR-2026-002', 'NAV-MR-COMPRESSOR-QUARTER', 'NAV-EQ-COMP-001', 'Kompressor vibratsiya o''lchovlarini bajarish', TIMESTAMP '2026-07-22 08:00:00', TIMESTAMP '2026-07-22 12:00:00', TIMESTAMP '2026-07-22 18:00:00', 'APPROVED', 'HIGH', 4.0::double precision, NULL::double precision),
        (11, 'NAV-PPR-2026-002', 'NAV-MR-COMPRESSOR-QUARTER', 'NAV-EQ-COMP-001', 'Bosim datchiklari ko''rsatkichini solishtirish', TIMESTAMP '2026-07-23 09:00:00', TIMESTAMP '2026-07-23 12:30:00', TIMESTAMP '2026-07-23 18:00:00', 'IN_PROGRESS', 'MEDIUM', 3.5::double precision, NULL::double precision),
        (12, 'NAV-PPR-2026-002', 'NAV-MR-COMPRESSOR-QUARTER', 'NAV-EQ-COMP-001', 'Sovitish konturini ko''rikdan o''tkazish', TIMESTAMP '2026-07-24 08:30:00', TIMESTAMP '2026-07-24 12:30:00', TIMESTAMP '2026-07-24 18:00:00', 'PLANNED', 'HIGH', 4.0::double precision, NULL::double precision),
        (13, 'NAV-PPR-2026-002', 'NAV-MR-COMPRESSOR-QUARTER', 'NAV-EQ-COMP-001', 'Filtr holatini nazorat qilish', TIMESTAMP '2026-07-07 08:00:00', TIMESTAMP '2026-07-07 10:30:00', TIMESTAMP '2026-07-07 18:00:00', 'COMPLETED', 'MEDIUM', 2.5::double precision, 2.5::double precision),
        (14, 'NAV-PPR-2026-002', 'NAV-MR-COMPRESSOR-QUARTER', 'NAV-EQ-COMP-001', 'Himoya blokirovka zanjirini tekshirish', TIMESTAMP '2026-07-28 09:00:00', TIMESTAMP '2026-07-28 13:00:00', TIMESTAMP '2026-07-28 18:00:00', 'APPROVED', 'CRITICAL', 4.0::double precision, NULL::double precision),
        (15, 'NAV-PPR-2026-002', 'NAV-MR-COMPRESSOR-QUARTER', 'NAV-EQ-COMP-001', 'Choraklik xizmat yakuniy ko''rigi', TIMESTAMP '2026-07-30 09:00:00', TIMESTAMP '2026-07-30 12:00:00', TIMESTAMP '2026-07-30 18:00:00', 'PLANNED', 'MEDIUM', 3.0::double precision, NULL::double precision),
        (16, 'NAV-PPR-2026-003', 'NAV-MR-INSTRUMENT-CALIBRATION', 'NAV-EQ-CABINET-001', 'NQOA signal zanjirlarini kalibrovka qilish', TIMESTAMP '2026-07-13 08:00:00', TIMESTAMP '2026-07-13 12:00:00', TIMESTAMP '2026-07-13 18:00:00', 'IN_PROGRESS', 'HIGH', 4.0::double precision, NULL::double precision),
        (17, 'NAV-PPR-2026-003', 'NAV-MR-MOTOR-QUARTER', 'NAV-EQ-MOTOR-001', 'Himoya rele sozlamalarini tekshirish', TIMESTAMP '2026-07-14 08:00:00', TIMESTAMP '2026-07-14 11:30:00', TIMESTAMP '2026-07-14 18:00:00', 'APPROVED', 'HIGH', 3.5::double precision, NULL::double precision),
        (18, 'NAV-PPR-2026-003', 'NAV-MR-GENERATOR-ANNUAL', 'NAV-EQ-GEN-001', 'Generator yuklama rejimini nazorat qilish', TIMESTAMP '2026-07-08 10:00:00', TIMESTAMP '2026-07-08 14:00:00', TIMESTAMP '2026-07-08 18:00:00', 'COMPLETED', 'HIGH', 4.0::double precision, 4.0::double precision),
        (19, 'NAV-PPR-2026-003', 'NAV-MR-INSTRUMENT-CALIBRATION', 'NAV-EQ-CABINET-001', 'Kontaktor va signal terminalini mahkamlash', TIMESTAMP '2026-08-04 08:00:00', TIMESTAMP '2026-08-04 11:00:00', TIMESTAMP '2026-08-04 18:00:00', 'PLANNED', 'MEDIUM', 3.0::double precision, NULL::double precision),
        (20, 'NAV-PPR-2026-003', 'NAV-MR-MOTOR-QUARTER', 'NAV-EQ-MOTOR-001', 'Elektr dvigatel izolyatsiyasini o''lchash', TIMESTAMP '2026-08-06 08:00:00', TIMESTAMP '2026-08-06 12:00:00', TIMESTAMP '2026-08-06 18:00:00', 'PLANNED', 'HIGH', 4.0::double precision, NULL::double precision),
        (21, 'NAV-PPR-2026-003', 'NAV-MR-GENERATOR-ANNUAL', 'NAV-EQ-GEN-001', 'Generator moy bosimi datchigini tekshirish', TIMESTAMP '2026-08-08 08:30:00', TIMESTAMP '2026-08-08 11:30:00', TIMESTAMP '2026-08-08 18:00:00', 'APPROVED', 'MEDIUM', 3.0::double precision, NULL::double precision),
        (22, 'NAV-PPR-2026-003', 'NAV-MR-INSTRUMENT-CALIBRATION', 'NAV-EQ-CABINET-001', 'Boshqaruv shkafi ventilyatsiyasini nazorat qilish', TIMESTAMP '2026-08-10 08:00:00', TIMESTAMP '2026-08-10 10:30:00', TIMESTAMP '2026-08-10 18:00:00', 'IN_PROGRESS', 'MEDIUM', 2.5::double precision, NULL::double precision),
        (23, 'NAV-PPR-2026-003', 'NAV-MR-MOTOR-QUARTER', 'NAV-EQ-MOTOR-001', 'Kabel ulanish nuqtalarini tekshirish', TIMESTAMP '2026-08-12 08:00:00', TIMESTAMP '2026-08-12 10:30:00', TIMESTAMP '2026-08-12 18:00:00', 'PLANNED', 'MEDIUM', 2.5::double precision, NULL::double precision),
        (24, 'NAV-PPR-2026-004', 'NAV-MR-VEHICLE-TRUCK-MONTHLY', 'NAV-VEH-VAN-001', 'Transport tormoz tizimini tekshirish', TIMESTAMP '2026-07-17 08:00:00', TIMESTAMP '2026-07-17 11:00:00', TIMESTAMP '2026-07-17 18:00:00', 'PLANNED', 'HIGH', 3.0::double precision, NULL::double precision),
        (25, 'NAV-PPR-2026-004', 'NAV-MR-VEHICLE-SPECIAL-MONTHLY', 'NAV-VEH-CRANE-001', 'Gidravlik tizim bosimini nazorat qilish', TIMESTAMP '2026-07-18 08:00:00', TIMESTAMP '2026-07-18 12:00:00', TIMESTAMP '2026-07-18 18:00:00', 'APPROVED', 'CRITICAL', 4.0::double precision, NULL::double precision),
        (26, 'NAV-PPR-2026-004', 'NAV-MR-VEHICLE-SPECIAL-MONTHLY', 'NAV-VEH-FORKLIFT-001', 'Avtoyuklagich vilkasi va gidravlik uzelini tekshirish', TIMESTAMP '2026-07-19 08:00:00', TIMESTAMP '2026-07-19 11:30:00', TIMESTAMP '2026-07-19 18:00:00', 'IN_PROGRESS', 'HIGH', 3.5::double precision, NULL::double precision),
        (27, 'NAV-PPR-2026-004', 'NAV-MR-VEHICLE-TRUCK-MONTHLY', 'NAV-VEH-FLATBED-001', 'Shina bosimi va rul boshqaruvini tekshirish', TIMESTAMP '2026-07-08 08:00:00', TIMESTAMP '2026-07-08 10:00:00', TIMESTAMP '2026-07-08 18:00:00', 'COMPLETED', 'MEDIUM', 2.0::double precision, 2.0::double precision),
        (28, 'NAV-PPR-2026-004', 'NAV-MR-VEHICLE-SPECIAL-MONTHLY', 'NAV-VEH-WELDING-001', 'Payvandlash servis mashinasi generatorini nazorat qilish', TIMESTAMP '2026-07-25 09:00:00', TIMESTAMP '2026-07-25 12:00:00', TIMESTAMP '2026-07-25 18:00:00', 'PLANNED', 'HIGH', 3.0::double precision, NULL::double precision),
        (29, 'NAV-PPR-2026-004', 'NAV-MR-VEHICLE-TRUCK-MONTHLY', 'NAV-VEH-VAN-001', 'Moy va filtr almashinuvi tayyorgarligini tekshirish', TIMESTAMP '2026-07-26 08:00:00', TIMESTAMP '2026-07-26 11:00:00', TIMESTAMP '2026-07-26 18:00:00', 'APPROVED', 'MEDIUM', 3.0::double precision, NULL::double precision),
        (30, 'NAV-PPR-2026-004', 'NAV-MR-VEHICLE-SPECIAL-MONTHLY', 'NAV-VEH-CRANE-001', 'Kran tayanch moslamalarini ko''rikdan o''tkazish', TIMESTAMP '2026-07-27 08:00:00', TIMESTAMP '2026-07-27 12:00:00', TIMESTAMP '2026-07-27 18:00:00', 'PLANNED', 'CRITICAL', 4.0::double precision, NULL::double precision),
        (31, 'NAV-PPR-2026-004', 'NAV-MR-VEHICLE-SPECIAL-MONTHLY', 'NAV-VEH-FORKLIFT-001', 'Yuklagich tormoz va signal tizimini tekshirish', TIMESTAMP '2026-07-29 08:00:00', TIMESTAMP '2026-07-29 11:30:00', TIMESTAMP '2026-07-29 18:00:00', 'IN_PROGRESS', 'HIGH', 3.5::double precision, NULL::double precision),
        (32, 'NAV-PPR-2026-004', 'NAV-MR-VEHICLE-TRUCK-MONTHLY', 'NAV-VEH-FLATBED-001', 'Bortli yuk mashinasi yoritish zanjirini nazorat qilish', TIMESTAMP '2026-07-07 13:00:00', TIMESTAMP '2026-07-07 15:00:00', TIMESTAMP '2026-07-07 18:00:00', 'COMPLETED', 'MEDIUM', 2.0::double precision, 1.75::double precision),
        (33, 'NAV-PPR-2026-004', 'NAV-MR-VEHICLE-SPECIAL-MONTHLY', 'NAV-VEH-WELDING-001', 'Servis kuzovi mahkamlash nuqtalarini tekshirish', TIMESTAMP '2026-07-31 08:00:00', TIMESTAMP '2026-07-31 10:30:00', TIMESTAMP '2026-07-31 18:00:00', 'PLANNED', 'MEDIUM', 2.5::double precision, NULL::double precision),
        (34, 'NAV-PPR-2026-005', 'NAV-MR-PIPELINE-ANNUAL', 'NAV-EQ-PIPE-001', 'Yillik ta''mir oldi defektatsiya ro''yxatini tayyorlash', TIMESTAMP '2026-08-15 08:00:00', TIMESTAMP '2026-08-15 16:00:00', TIMESTAMP '2026-08-15 18:00:00', 'PLANNED', 'HIGH', 8.0::double precision, NULL::double precision),
        (35, 'NAV-PPR-2026-005', 'NAV-MR-PIPELINE-ANNUAL', 'NAV-EQ-PIPE-001', 'Quvur tayanchlarini geometriya bo''yicha tekshirish', TIMESTAMP '2026-08-18 08:00:00', TIMESTAMP '2026-08-18 14:00:00', TIMESTAMP '2026-08-18 18:00:00', 'APPROVED', 'HIGH', 6.0::double precision, NULL::double precision),
        (36, 'NAV-PPR-2026-005', 'NAV-MR-PIPELINE-ANNUAL', 'NAV-EQ-PIPE-001', 'Flanes birikmalari boltlarini nazorat qilish', TIMESTAMP '2026-08-20 08:00:00', TIMESTAMP '2026-08-20 12:00:00', TIMESTAMP '2026-08-20 18:00:00', 'PLANNED', 'MEDIUM', 4.0::double precision, NULL::double precision),
        (37, 'NAV-PPR-2026-005', 'NAV-MR-GENERATOR-ANNUAL', 'NAV-EQ-GEN-001', 'Generator yillik xizmat hajmini aniqlash', TIMESTAMP '2026-08-22 08:00:00', TIMESTAMP '2026-08-22 12:00:00', TIMESTAMP '2026-08-22 18:00:00', 'IN_PROGRESS', 'HIGH', 4.0::double precision, NULL::double precision),
        (38, 'NAV-PPR-2026-005', 'NAV-MR-PIPELINE-ANNUAL', 'NAV-EQ-PIPE-001', 'Korroziya joylarini belgilash va o''lchash', TIMESTAMP '2026-08-09 08:00:00', TIMESTAMP '2026-08-09 14:00:00', TIMESTAMP '2026-08-09 18:00:00', 'OVERDUE', 'HIGH', 6.0::double precision, NULL::double precision),
        (39, 'NAV-PPR-2026-005', 'NAV-MR-GENERATOR-ANNUAL', 'NAV-EQ-GEN-001', 'Generator zaxira rejimi tayyorgarligini tekshirish', TIMESTAMP '2026-08-25 08:00:00', TIMESTAMP '2026-08-25 11:30:00', TIMESTAMP '2026-08-25 18:00:00', 'PLANNED', 'HIGH', 3.5::double precision, NULL::double precision),
        (40, 'NAV-PPR-2026-005', 'NAV-MR-PIPELINE-ANNUAL', 'NAV-EQ-PIPE-001', 'Kapital ta''mir material ehtiyojini yakuniy kelishish', TIMESTAMP '2026-08-28 08:00:00', TIMESTAMP '2026-08-28 13:00:00', TIMESTAMP '2026-08-28 18:00:00', 'APPROVED', 'MEDIUM', 5.0::double precision, NULL::double precision)
    ) AS v(idx, plan_code, regulation_code, equipment_code, title, scheduled_start, scheduled_end, due_date, status, priority, planned_labor_hours, actual_labor_hours)
)
INSERT INTO ppr_tasks (
    id, created_at, updated_at, is_deleted,
    code, plan_id, regulation_id, equipment_id, cycle_key, title,
    scheduled_start, scheduled_end, due_date, status, priority,
    planned_labor_hours, actual_labor_hours, postpone_reason
)
SELECT ('71000000-0000-0000-0000-00000013' || lpad(tv.idx::text, 4, '0'))::uuid,
       now(), now(), false,
       'NAV-PPR-TASK-2026-' || lpad(tv.idx::text, 4, '0'),
       p.id, mr.id, e.id,
       tv.plan_code || ':' || lpad(tv.idx::text, 4, '0'),
       tv.title, tv.scheduled_start, tv.scheduled_end, tv.due_date,
       tv.status, tv.priority, tv.planned_labor_hours, tv.actual_labor_hours, NULL
FROM task_values tv
JOIN ppr_plans p ON p.code = tv.plan_code AND p.is_deleted = false
JOIN maintenance_regulations mr ON mr.code = tv.regulation_code AND mr.is_deleted = false
JOIN equipment e ON e.code = tv.equipment_code AND e.is_deleted = false
ON CONFLICT (code) DO UPDATE
SET plan_id = EXCLUDED.plan_id,
    regulation_id = EXCLUDED.regulation_id,
    equipment_id = EXCLUDED.equipment_id,
    cycle_key = EXCLUDED.cycle_key,
    title = EXCLUDED.title,
    scheduled_start = EXCLUDED.scheduled_start,
    scheduled_end = EXCLUDED.scheduled_end,
    due_date = EXCLUDED.due_date,
    status = EXCLUDED.status,
    priority = EXCLUDED.priority,
    planned_labor_hours = EXCLUDED.planned_labor_hours,
    actual_labor_hours = EXCLUDED.actual_labor_hours,
    postpone_reason = EXCLUDED.postpone_reason,
    is_deleted = false,
    updated_at = now();
