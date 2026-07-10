WITH actor AS (
    SELECT
        (SELECT id FROM users WHERE username = 'nav.azot.planner' AND is_deleted = false) AS planner_id,
        (SELECT id FROM users WHERE username = 'nav.azot.chief.mechanic' AND is_deleted = false) AS chief_mechanic_id,
        (SELECT id FROM users WHERE username = 'nav.azot.mech.foreman' AND is_deleted = false) AS mechanic_foreman_id,
        (SELECT id FROM users WHERE username = 'nav.azot.elec.foreman' AND is_deleted = false) AS electrical_foreman_id,
        (SELECT id FROM users WHERE username = 'nav.azot.transport.foreman' AND is_deleted = false) AS transport_foreman_id
),
request_plan AS (
    SELECT *
    FROM (VALUES
        (1, 'NAV-RR-2026-0001', 'NAV-EQ-PUMP-001', 'Nasos agregatida vibratsiya oshishi', 'Ammiak sexi sirkulyatsion nasosida podshipnik zonasida vibratsiya oshgani qayd etildi.', 'HIGH', 'HIGH', 'IN_PROGRESS', 'INSPECTION', 'mechanic'),
        (2, 'NAV-RR-2026-0002', 'NAV-EQ-COMP-001', 'Kompressor bosimi pasayishi', 'Kompressor chiqish bosimi ish rejimida meyordan past bo''lib qoldi.', 'CRITICAL', 'CRITICAL', 'ASSIGNED', 'OPERATOR', 'mechanic'),
        (3, 'NAV-RR-2026-0003', 'NAV-EQ-HE-001', 'Issiqlik almashinish apparatida sizib chiqish', 'Karbamid blokidagi issiqlik almashinish apparatida flanes birikmasida sizish belgisi aniqlandi.', 'HIGH', 'HIGH', 'APPROVED', 'MANUAL', 'mechanic'),
        (4, 'NAV-RR-2026-0004', 'NAV-EQ-REACTOR-001', 'Reaktor uzelida harorat beqarorligi', 'Reaktor yordamchi uzelida harorat ko''rsatkichi qisqa vaqt oralig''ida tebranmoqda.', 'HIGH', 'HIGH', 'IN_PROGRESS', 'OPERATOR', 'mechanic'),
        (5, 'NAV-RR-2026-0005', 'NAV-EQ-FAN-001', 'Ventilyator agregatida shovqin', 'Sovitish minorasi ventilyatorida notekis shovqin va tebranish paydo bo''ldi.', 'MEDIUM', 'MEDIUM', 'OPEN', 'INSPECTION', 'mechanic'),
        (6, 'NAV-RR-2026-0006', 'NAV-EQ-MOTOR-001', 'Elektr dvigatel podshipnigida qizish', 'Elektr dvigatel old podshipnik qismida qizish kuzatildi.', 'HIGH', 'HIGH', 'APPROVED', 'MANUAL', 'electrical'),
        (7, 'NAV-RR-2026-0007', 'NAV-EQ-CABINET-001', 'NQOA shkafida signal uzilishi', 'Boshqaruv shkafida nasos holati signali qisqa muddat uzilib qolmoqda.', 'MEDIUM', 'MEDIUM', 'ASSIGNED', 'OPERATOR', 'electrical'),
        (8, 'NAV-RR-2026-0008', 'NAV-EQ-GEN-001', 'Dizel generatorda moy bosimi ogohlantirishi', 'Zaxira generator ishga tushirilganda moy bosimi bo''yicha ogohlantirish chiqdi.', 'HIGH', 'HIGH', 'IN_PROGRESS', 'MANUAL', 'electrical'),
        (9, 'NAV-RR-2026-0009', 'NAV-EQ-PIPE-001', 'Quvur liniyasida flanes zichligi buzilishi', 'Azot kislotasi liniyasidagi DN150 flanesda namlanish izlari bor.', 'CRITICAL', 'CRITICAL', 'COMPLETED', 'INSPECTION', 'mechanic'),
        (10, 'NAV-RR-2026-0010', 'NAV-EQ-PUMP-002', 'Zaxira nasos ishga tushishida kechikish', 'Zaxira nasos avtomatik ishga tushishida kechikish qayd etildi.', 'MEDIUM', 'MEDIUM', 'CLOSED', 'OPERATOR', 'mechanic'),
        (11, 'NAV-RR-2026-0011', 'NAV-VEH-PICKUP-001', 'Servis pikapida tormoz tizimi tekshiruvi', 'Xizmat pikapida tormoz bosimi va kolodka holatini tekshirish zarur.', 'MEDIUM', 'MEDIUM', 'APPROVED', 'MOBILE', 'transport'),
        (12, 'NAV-RR-2026-0012', 'NAV-VEH-VAN-001', 'GAZel servis avtomobilida moy almashtirish', 'Servis furgonida rejalashtirilgan moy va filtr almashtirish muddati keldi.', 'LOW', 'LOW', 'IN_PROGRESS', 'MOBILE', 'transport'),
        (13, 'NAV-RR-2026-0013', 'NAV-VEH-CRANE-001', 'Avtokran gidravlik shlangini almashtirish', 'Avtokran tayanch konturida gidravlik shlang yuzasida yoriq aniqlandi.', 'HIGH', 'HIGH', 'ASSIGNED', 'INSPECTION', 'transport'),
        (14, 'NAV-RR-2026-0014', 'NAV-VEH-FORKLIFT-001', 'Yuk ortuvchi texnikada filtrlar almashinuvi', 'Avtoyuklagichda moy va havo filtrlari navbatdagi xizmatga yetdi.', 'MEDIUM', 'MEDIUM', 'APPROVED', 'MOBILE', 'transport'),
        (15, 'NAV-RR-2026-0015', 'NAV-VEH-FUEL-001', 'Yoqilgi servis avtomobilida nasos tekshiruvi', 'Yoqilgi servis avtomobilidagi uzatish nasosida unum pasayishi kuzatildi.', 'HIGH', 'HIGH', 'IN_PROGRESS', 'OPERATOR', 'transport'),
        (16, 'NAV-RR-2026-0016', 'NAV-VEH-BUS-001', 'Xodim tashish avtobusida texnik korik oldi tayyorgarlik', 'Avtobusni navbatdagi texnik ko''rik oldidan xizmatga tayyorlash zarur.', 'MEDIUM', 'MEDIUM', 'APPROVED', 'MANUAL', 'transport'),
        (17, 'NAV-RR-2026-0017', 'NAV-VEH-FLATBED-001', 'Bortli yuk avtomobilida shina yeyilishi', 'Bortli yuk avtomobilida old o''q shinalarida notekis yeyilish qayd etildi.', 'MEDIUM', 'MEDIUM', 'OPEN', 'MOBILE', 'transport'),
        (18, 'NAV-RR-2026-0018', 'NAV-VEH-EMERGENCY-001', 'Favqulodda xizmat avtomobilida yoritish nosozligi', 'Tezkor javob avtomobilida chap old yoritish zanjiri ishlamayapti.', 'HIGH', 'HIGH', 'ASSIGNED', 'MOBILE', 'transport'),
        (19, 'NAV-RR-2026-0019', 'NAV-VEH-WELDING-001', 'Payvandlash servis avtomobilida generator tekshiruvi', 'Payvandlash servis avtomobilidagi generator yuklama ostida barqaror ishlamayapti.', 'HIGH', 'HIGH', 'IN_PROGRESS', 'OPERATOR', 'transport'),
        (20, 'NAV-RR-2026-0020', 'NAV-VEH-COMPRESSOR-TRAILER-001', 'Mobil kompressor treylerida bosim regulyatori sozlanishi', 'Ko''chma kompressor tirkamasida bosim regulyatori sozlanishi talab qilinadi.', 'MEDIUM', 'MEDIUM', 'CLOSED', 'INSPECTION', 'transport')
    ) AS v(idx, number, equipment_code, title, description, priority, criticality, status, source, assignee_group)
)
INSERT INTO repair_requests (
    id, created_at, updated_at, is_deleted,
    number, title, description, equipment_id, department_id, location_id,
    reporter_id, assigned_to_id, priority, criticality, status, source,
    detected_at, target_completion_at, actual_completion_at, reacted_at, close_result
)
SELECT ('71000000-0000-0000-0000-00000002' || lpad(rp.idx::text, 4, '0'))::uuid,
       now(), now(), false,
       rp.number, rp.title, rp.description, e.id, e.department_id, e.location_id,
       COALESCE(actor.planner_id, actor.chief_mechanic_id),
       CASE rp.assignee_group
           WHEN 'electrical' THEN actor.electrical_foreman_id
           WHEN 'transport' THEN actor.transport_foreman_id
           ELSE actor.mechanic_foreman_id
       END,
       rp.priority, rp.criticality, rp.status, rp.source,
       TIMESTAMP '2026-07-01 08:00:00' + ((rp.idx - 1) || ' days')::interval,
       TIMESTAMP '2026-07-03 18:00:00' + ((rp.idx - 1) || ' days')::interval,
       CASE WHEN rp.status IN ('COMPLETED','CLOSED') THEN TIMESTAMP '2026-07-02 16:30:00' + ((rp.idx - 1) || ' days')::interval ELSE NULL END,
       CASE WHEN rp.status <> 'OPEN' THEN TIMESTAMP '2026-07-01 10:00:00' + ((rp.idx - 1) || ' days')::interval ELSE NULL END,
       CASE WHEN rp.status = 'CLOSED' THEN 'Ishlar yakunlandi va uskunaning ishga tayyorligi tasdiqlandi' ELSE NULL END
FROM request_plan rp
JOIN equipment e ON e.code = rp.equipment_code AND e.is_deleted = false
CROSS JOIN actor
ON CONFLICT (number) DO UPDATE
SET title = EXCLUDED.title,
    description = EXCLUDED.description,
    equipment_id = EXCLUDED.equipment_id,
    department_id = EXCLUDED.department_id,
    location_id = EXCLUDED.location_id,
    reporter_id = EXCLUDED.reporter_id,
    assigned_to_id = EXCLUDED.assigned_to_id,
    priority = EXCLUDED.priority,
    criticality = EXCLUDED.criticality,
    status = EXCLUDED.status,
    source = EXCLUDED.source,
    detected_at = EXCLUDED.detected_at,
    target_completion_at = EXCLUDED.target_completion_at,
    actual_completion_at = EXCLUDED.actual_completion_at,
    reacted_at = EXCLUDED.reacted_at,
    close_result = EXCLUDED.close_result,
    is_deleted = false,
    updated_at = now();

WITH defect_plan AS (
    SELECT *
    FROM (VALUES
        (1, 'NAV-DEF-2026-0001', 'NAV-RR-2026-0001', 'Podshipnik yeyilishi', 'Nasos podshipnikida radial lyuft va shovqin kuchaygan.', 'Mexanik', 'Yuqori', 'Moylash rejimi yetarli emas', 'Podshipnik xizmat muddati tugagan', 'IN_PROGRESS', 1),
        (2, 'NAV-DEF-2026-0002', 'NAV-RR-2026-0002', 'Salnikdan sizib chiqish', 'Kompressor moy konturida salnikdan mayda sizish kuzatildi.', 'Mexanik', 'Kritik', 'Zichlagich yeyilishi', 'Salnik yuzasi shikastlangan', 'OPEN', 0),
        (3, 'NAV-DEF-2026-0003', 'NAV-RR-2026-0003', 'Flanes prokladkasi shikastlangan', 'Issiqlik almashinish apparati flanesida prokladka shikastlanish belgisi bor.', 'Texnologik', 'Yuqori', 'Harorat sikllari', 'Prokladka elastikligi pasaygan', 'IN_PROGRESS', 1),
        (4, 'NAV-DEF-2026-0004', 'NAV-RR-2026-0004', 'Manometr kalibrovkadan chiqqan', 'Reaktor uzeli ko''rsatkichlari nazorat asboblari bilan farq qilmoqda.', 'Texnologik', 'Yuqori', 'O''lchov driftlari', 'Davriy kalibrlash zarur', 'OPEN', 0),
        (5, 'NAV-DEF-2026-0005', 'NAV-RR-2026-0005', 'Kamar tarangligi yetarli emas', 'Ventilyator yuritma kamarida sirpanish va notekis taranglik bor.', 'Mexanik', 'Orta', 'Kamar cho''zilishi', 'Taranglash sozlamasi buzilgan', 'OPEN', 0),
        (6, 'NAV-DEF-2026-0006', 'NAV-RR-2026-0006', 'Podshipnik qizishi', 'Elektr dvigatel podshipnik qismida harorat yuqori.', 'Elektr', 'Yuqori', 'Yuklama va moylash holati', 'Podshipnik moyi ifloslangan', 'IN_PROGRESS', 1),
        (7, 'NAV-DEF-2026-0007', 'NAV-RR-2026-0007', 'Kontaktor kontaktlari kuygan', 'Boshqaruv shkafida kontaktor kontaktlarida kuyish izlari bor.', 'Elektr', 'Orta', 'Yuqori ulanish qarshiligi', 'Kontakt yuzasi eskirgan', 'OPEN', 0),
        (8, 'NAV-DEF-2026-0008', 'NAV-RR-2026-0008', 'Moy filtri tiqilgan', 'Generator moy tizimida filtr bosim farqi oshgan.', 'Mexanik', 'Yuqori', 'Filtr elementi ifloslangan', 'Moy almashtirish oraligi cho''zilgan', 'IN_PROGRESS', 1),
        (9, 'NAV-DEF-2026-0009', 'NAV-RR-2026-0009', 'Quvur yuzasida korroziya', 'Flanes yaqinida mahalliy korroziya va namlanish izlari aniqlandi.', 'Texnologik', 'Kritik', 'Tashqi qoplama yemirilishi', 'Namlik ta''siri', 'RESOLVED', 2),
        (10, 'NAV-DEF-2026-0010', 'NAV-RR-2026-0010', 'Avtomatik ishga tushirish kechikishi', 'Zaxira nasos boshqaruv zanjirida kechikish qayd etildi.', 'Elektr', 'Orta', 'Signal kontaktlari sust ishlashi', 'Rele sozlamasi tekshiruv talab qiladi', 'RESOLVED', 1)
    ) AS v(idx, code, rr_number, title, description, category, severity, failure_reason, root_cause, status, recurrence_count)
)
INSERT INTO defects (
    id, created_at, updated_at, is_deleted,
    code, title, description, equipment_id, repair_request_id,
    category, severity, failure_reason, root_cause,
    status, detected_at, resolved_at, recurrence_count
)
SELECT ('71000000-0000-0000-0000-00000003' || lpad(dp.idx::text, 4, '0'))::uuid,
       now(), now(), false,
       dp.code, dp.title, dp.description, rr.equipment_id, rr.id,
       dp.category, dp.severity, dp.failure_reason, dp.root_cause,
       dp.status, rr.detected_at,
       CASE WHEN dp.status = 'RESOLVED' THEN rr.actual_completion_at ELSE NULL END,
       dp.recurrence_count
FROM defect_plan dp
JOIN repair_requests rr ON rr.number = dp.rr_number AND rr.is_deleted = false
ON CONFLICT (code) DO UPDATE
SET title = EXCLUDED.title,
    description = EXCLUDED.description,
    equipment_id = EXCLUDED.equipment_id,
    repair_request_id = EXCLUDED.repair_request_id,
    category = EXCLUDED.category,
    severity = EXCLUDED.severity,
    failure_reason = EXCLUDED.failure_reason,
    root_cause = EXCLUDED.root_cause,
    status = EXCLUDED.status,
    detected_at = EXCLUDED.detected_at,
    resolved_at = EXCLUDED.resolved_at,
    recurrence_count = EXCLUDED.recurrence_count,
    is_deleted = false,
    updated_at = now();

WITH actor AS (
    SELECT
        (SELECT id FROM users WHERE username = 'nav.azot.planner' AND is_deleted = false) AS planner_id,
        (SELECT id FROM users WHERE username = 'nav.azot.chief.mechanic' AND is_deleted = false) AS chief_mechanic_id
),
defect_list_plan AS (
    SELECT *
    FROM (VALUES
        (1, 'NAV-DL-2026-0001', 'NAV-RR-2026-0001', 'Nasos podshipnik ta''miri uchun nuqson dalolatnomasi', 'APPROVED', 6.0, 1420000.0, 'Podshipnik va moylash holati bo''yicha ish hajmi tasdiqlandi'),
        (2, 'NAV-DL-2026-0002', 'NAV-RR-2026-0002', 'Kompressor salnik uzeli bo''yicha nuqson dalolatnomasi', 'APPROVED', 8.0, 2500000.0, 'Salnik uzeli ko''rik va almashtirish ishlariga tayyor'),
        (3, 'NAV-DL-2026-0003', 'NAV-RR-2026-0003', 'Flanes zichligi tiklash dalolatnomasi', 'APPROVED', 5.0, 320000.0, 'Prokladka almashtirish va flanes tortish ishlari'),
        (4, 'NAV-DL-2026-0004', 'NAV-RR-2026-0004', 'Reaktor nazorat asboblari nuqson ro''yxati', 'APPROVED', 4.0, 720000.0, 'Manometr va signal tekshiruv ishlari'),
        (5, 'NAV-DL-2026-0005', 'NAV-RR-2026-0005', 'Ventilyator yuritmasi nuqson ro''yxati', 'DRAFT', 3.5, 410000.0, 'Kamar va podshipnik holati tekshiriladi'),
        (6, 'NAV-DL-2026-0006', 'NAV-RR-2026-0006', 'Elektr dvigatel podshipnik ro''yxati', 'APPROVED', 6.5, 980000.0, 'Podshipnik qizishi sababini bartaraf etish'),
        (7, 'NAV-DL-2026-0007', 'NAV-RR-2026-0007', 'Boshqaruv shkafi kontaktor ro''yxati', 'APPROVED', 3.0, 680000.0, 'Kontaktor kontaktlari va signal zanjiri tekshiriladi'),
        (8, 'NAV-DL-2026-0008', 'NAV-RR-2026-0008', 'Dizel generator moy tizimi ro''yxati', 'APPROVED', 4.5, 520000.0, 'Moy filtri va bosim nazorati ishlari'),
        (9, 'NAV-DL-2026-0009', 'NAV-RR-2026-0009', 'Azot kislotasi flanes ro''yxati', 'APPROVED', 7.0, 460000.0, 'Korroziya joyini tozalash va zichlikni tiklash'),
        (10, 'NAV-DL-2026-0010', 'NAV-RR-2026-0010', 'Zaxira nasos avtomatika ro''yxati', 'APPROVED', 3.0, 360000.0, 'Ishga tushirish zanjiri va rele tekshiruvi')
    ) AS v(idx, code, rr_number, title, status, total_labor_hours, total_estimated_cost, notes)
)
INSERT INTO defect_lists (
    id, created_at, updated_at, is_deleted, created_by_id, updated_by_id,
    code, title, equipment_id, repair_request_id, work_order_id, approved_by_id,
    status, total_labor_hours, total_estimated_cost, notes
)
SELECT ('71000000-0000-0000-0000-00000004' || lpad(dlp.idx::text, 4, '0'))::uuid,
       now(), now(), false, actor.planner_id, actor.planner_id,
       dlp.code, dlp.title, rr.equipment_id, rr.id, NULL,
       CASE WHEN dlp.status = 'APPROVED' THEN actor.chief_mechanic_id ELSE NULL END,
       dlp.status, dlp.total_labor_hours, dlp.total_estimated_cost, dlp.notes
FROM defect_list_plan dlp
JOIN repair_requests rr ON rr.number = dlp.rr_number AND rr.is_deleted = false
CROSS JOIN actor
ON CONFLICT (code) DO UPDATE
SET title = EXCLUDED.title,
    equipment_id = EXCLUDED.equipment_id,
    repair_request_id = EXCLUDED.repair_request_id,
    approved_by_id = EXCLUDED.approved_by_id,
    status = EXCLUDED.status,
    total_labor_hours = EXCLUDED.total_labor_hours,
    total_estimated_cost = EXCLUDED.total_estimated_cost,
    notes = EXCLUDED.notes,
    is_deleted = false,
    updated_by_id = EXCLUDED.updated_by_id,
    updated_at = now();

WITH line_plan AS (
    SELECT *
    FROM (VALUES
        (1, 1, 'NAV-DL-2026-0001', 'NAV-DEF-2026-0001', 'Podshipnik uzelini almashtirish', 'Eski podshipnikni yechish va yangi podshipnikni o''rnatish', 'Podshipnik 6312 va EP2 surtma', 'NAV-SP-BRG-6312', 1.0, 4.0, 420000.0, 'NATURAL_WEAR'),
        (2, 1, 'NAV-DL-2026-0001', 'NAV-DEF-2026-0001', 'Moylash tizimini tozalash', 'Podshipnik korpusini tozalash va moylash', 'Surtma EP2', 'NAV-SP-GREASE-EP2', 2.0, 2.0, 92000.0, 'NATURAL_WEAR'),
        (3, 2, 'NAV-DL-2026-0002', 'NAV-DEF-2026-0002', 'Salnik uzelini almashtirish', 'Kompressor salnik qopqog''ini ochish va zichlagichni almashtirish', 'Mexanik salnik 60 mm', 'NAV-SP-SEAL-60', 1.0, 8.0, 1680000.0, 'NATURAL_WEAR'),
        (4, 3, 'NAV-DL-2026-0003', 'NAV-DEF-2026-0003', 'Flanes prokladkasini almashtirish', 'Flanesni ochish, yuzani tozalash va prokladkani almashtirish', 'Flanes prokladka DN150', 'NAV-SP-GASKET-DN150', 2.0, 5.0, 92000.0, 'NATURAL_WEAR'),
        (5, 4, 'NAV-DL-2026-0004', 'NAV-DEF-2026-0004', 'Manometrni almashtirish', 'Asbobni ajratish va yangi manometrni o''rnatish', 'Manometr 0-16 bar', 'NAV-SP-GAUGE-16BAR', 1.0, 4.0, 360000.0, 'UNKNOWN'),
        (6, 5, 'NAV-DL-2026-0005', 'NAV-DEF-2026-0005', 'V-kamar tarangligini tiklash', 'Kamar holatini tekshirish va kerakli almashtirish', 'V-kamar C-2240', 'NAV-SP-BELT-C2240', 2.0, 3.5, 460000.0, 'NATURAL_WEAR'),
        (7, 6, 'NAV-DL-2026-0006', 'NAV-DEF-2026-0006', 'Elektr dvigatel podshipnigini almashtirish', 'Dvigatelni ajratish va podshipnikni almashtirish', 'Podshipnik 6208', 'NAV-SP-BRG-6208', 2.0, 6.5, 380000.0, 'NATURAL_WEAR'),
        (8, 7, 'NAV-DL-2026-0007', 'NAV-DEF-2026-0007', 'Kontaktorni almashtirish', 'Kuygan kontaktorni yechish va sozlash', 'Kontaktor 220V', 'NAV-SP-CONTACTOR-220', 1.0, 3.0, 680000.0, 'POOR_OPERATION'),
        (9, 8, 'NAV-DL-2026-0008', 'NAV-DEF-2026-0008', 'Moy filtrini almashtirish', 'Generator moy tizimi filtrini almashtirish va bosimni tekshirish', 'Moy filtri', 'NAV-SP-OIL-FILTER', 1.0, 4.5, 175000.0, 'NATURAL_WEAR'),
        (10, 9, 'NAV-DL-2026-0009', 'NAV-DEF-2026-0009', 'Flanes zichligini tiklash', 'Yuzani tozalash va prokladkani almashtirish', 'Flanes prokladka DN100', 'NAV-SP-GASKET-DN100', 2.0, 7.0, 64000.0, 'NATURAL_WEAR'),
        (11, 10, 'NAV-DL-2026-0010', 'NAV-DEF-2026-0010', 'Rele va signal zanjirini sozlash', 'Signal kontaktlarini tekshirish va sozlash', 'Avtomat himoya 63A', 'NAV-SP-BREAKER-63A', 1.0, 3.0, 520000.0, 'UNKNOWN')
    ) AS v(line_idx, list_idx, list_code, defect_code, description, work_scope, material_specification, spare_part_code, required_quantity, estimated_labor_hours, estimated_cost, defect_origin)
)
INSERT INTO defect_list_lines (
    id, created_at, updated_at, is_deleted,
    defect_list_id, defect_id, defect_origin, description, work_scope,
    material_specification, spare_part_id, required_quantity, estimated_labor_hours, estimated_cost
)
SELECT ('71000000-0000-0000-0000-00000005' || lpad(lp.line_idx::text, 4, '0'))::uuid,
       now(), now(), false,
       dl.id, d.id, lp.defect_origin, lp.description, lp.work_scope,
       lp.material_specification, sp.id, lp.required_quantity, lp.estimated_labor_hours, lp.estimated_cost
FROM line_plan lp
JOIN defect_lists dl ON dl.code = lp.list_code AND dl.is_deleted = false
JOIN defects d ON d.code = lp.defect_code AND d.is_deleted = false
LEFT JOIN spare_parts sp ON sp.code = lp.spare_part_code AND sp.is_deleted = false
ON CONFLICT (id) DO UPDATE
SET defect_list_id = EXCLUDED.defect_list_id,
    defect_id = EXCLUDED.defect_id,
    defect_origin = EXCLUDED.defect_origin,
    description = EXCLUDED.description,
    work_scope = EXCLUDED.work_scope,
    material_specification = EXCLUDED.material_specification,
    spare_part_id = EXCLUDED.spare_part_id,
    required_quantity = EXCLUDED.required_quantity,
    estimated_labor_hours = EXCLUDED.estimated_labor_hours,
    estimated_cost = EXCLUDED.estimated_cost,
    is_deleted = false,
    updated_at = now();

WITH actor AS (
    SELECT
        (SELECT id FROM users WHERE username = 'nav.azot.planner' AND is_deleted = false) AS planner_id,
        (SELECT id FROM users WHERE username = 'nav.azot.chief.mechanic' AND is_deleted = false) AS chief_mechanic_id,
        (SELECT id FROM users WHERE username = 'nav.azot.mech.foreman' AND is_deleted = false) AS mechanic_foreman_id,
        (SELECT id FROM users WHERE username = 'nav.azot.elec.foreman' AND is_deleted = false) AS electrical_foreman_id,
        (SELECT id FROM users WHERE username = 'nav.azot.transport.foreman' AND is_deleted = false) AS transport_foreman_id
),
wo_plan AS (
    SELECT *
    FROM (VALUES
        (1, 'NAV-WO-2026-0001', 'NAV-RR-2026-0001', 'NAV-DEF-2026-0001', 'NAV-DL-2026-0001', 'Podshipnik va moylash tizimini tiklash', 'DEFECT', 'IN_PROGRESS', 'HIGH', true, false, true, 'mechanic', 'Podshipnik almashtirish ishlari bajarilmoqda', NULL),
        (2, 'NAV-WO-2026-0002', 'NAV-RR-2026-0002', 'NAV-DEF-2026-0002', 'NAV-DL-2026-0002', 'Kompressor salnik uzelini tiklash', 'DEFECT', 'APPROVED', 'CRITICAL', true, true, true, 'mechanic', 'Salnik uzeli bo''yicha ta''mirlash tayyorlandi', NULL),
        (3, 'NAV-WO-2026-0003', 'NAV-RR-2026-0003', 'NAV-DEF-2026-0003', 'NAV-DL-2026-0003', 'Flanes zichligini tiklash', 'DEFECT', 'COMPLETED', 'HIGH', true, false, true, 'mechanic', 'Flanes prokladkasi almashtirildi', 'Zichlik tekshiruvi qoniqarli'),
        (4, 'NAV-WO-2026-0004', 'NAV-RR-2026-0004', 'NAV-DEF-2026-0004', 'NAV-DL-2026-0004', 'Reaktor nazorat asbobini sozlash', 'DEFECT', 'IN_PROGRESS', 'HIGH', true, false, true, 'mechanic', 'Nazorat asbobi almashtirishga tayyor', NULL),
        (5, 'NAV-WO-2026-0005', 'NAV-RR-2026-0005', 'NAV-DEF-2026-0005', 'NAV-DL-2026-0005', 'Ventilyator yuritmasini ko''rikdan o''tkazish', 'DEFECT', 'APPROVED', 'MEDIUM', false, false, true, 'mechanic', 'Kamar tarangligi tiklanadi', NULL),
        (6, 'NAV-WO-2026-0006', 'NAV-RR-2026-0006', 'NAV-DEF-2026-0006', 'NAV-DL-2026-0006', 'Elektr dvigatel podshipnik qismini tiklash', 'DEFECT', 'COMPLETED', 'HIGH', true, false, true, 'electrical', 'Podshipnik qizishi bartaraf etildi', 'Dvigatel ishga tayyor'),
        (7, 'NAV-WO-2026-0007', 'NAV-RR-2026-0007', 'NAV-DEF-2026-0007', 'NAV-DL-2026-0007', 'Boshqaruv shkafi kontaktorlarini tekshirish', 'DEFECT', 'PLANNED', 'MEDIUM', false, false, false, 'electrical', 'Kontaktor zanjiri rejalashtirilgan', NULL),
        (8, 'NAV-WO-2026-0008', 'NAV-RR-2026-0008', 'NAV-DEF-2026-0008', 'NAV-DL-2026-0008', 'Dizel generator moy bosimini tekshirish', 'DEFECT', 'IN_PROGRESS', 'HIGH', false, false, false, 'electrical', 'Bosim nazorati o''tkazilmoqda', NULL),
        (9, 'NAV-WO-2026-0009', 'NAV-RR-2026-0009', 'NAV-DEF-2026-0009', 'NAV-DL-2026-0009', 'Quvur flanesini korroziyadan tozalash', 'DEFECT', 'CLOSED', 'CRITICAL', true, true, false, 'mechanic', 'Korroziya joyi tozalandi', 'Quvur liniyasi nazoratdan o''tdi'),
        (10, 'NAV-WO-2026-0010', 'NAV-RR-2026-0010', 'NAV-DEF-2026-0010', 'NAV-DL-2026-0010', 'Zaxira nasos avtomatika zanjirini sozlash', 'DEFECT', 'CLOSED', 'MEDIUM', false, false, false, 'electrical', 'Avtomatika zanjiri sozlandi', 'Ishga tushirish vaqti meyorga keldi'),
        (11, 'NAV-WO-2026-0011', 'NAV-RR-2026-0011', NULL, NULL, 'Servis pikapi tormoz tizimi xizmati', 'PLANNED', 'APPROVED', 'MEDIUM', false, false, true, 'transport', 'Tormoz tizimi ko''rik va material bilan xizmat qilinadi', NULL),
        (12, 'NAV-WO-2026-0012', 'NAV-RR-2026-0012', NULL, NULL, 'GAZel servis avtomobilida moy va filtr almashtirish', 'PLANNED', 'IN_PROGRESS', 'LOW', false, false, true, 'transport', 'Moy va filtrlar almashtirilmoqda', NULL),
        (13, 'NAV-WO-2026-0013', 'NAV-RR-2026-0013', NULL, NULL, 'Avtokran gidravlik shlangini almashtirish', 'PLANNED', 'APPROVED', 'HIGH', true, false, true, 'transport', 'Gidravlik shlang almashtirish ishlari tayyor', NULL),
        (14, 'NAV-WO-2026-0014', 'NAV-RR-2026-0014', NULL, NULL, 'Avtoyuklagich filtrlarini almashtirish', 'PLANNED', 'COMPLETED', 'MEDIUM', false, false, true, 'transport', 'Filtrlar almashtirildi', 'Yuklagich ishga tayyor'),
        (15, 'NAV-WO-2026-0015', 'NAV-RR-2026-0015', NULL, NULL, 'Yoqilgi servis avtomobili nasosini tekshirish', 'INSPECTION', 'IN_PROGRESS', 'HIGH', false, false, true, 'transport', 'Nasos unumdorligi tekshirilmoqda', NULL),
        (16, 'NAV-WO-2026-0016', 'NAV-RR-2026-0016', NULL, NULL, 'Avtobus texnik korik oldi tayyorgarligi', 'INSPECTION', 'APPROVED', 'MEDIUM', false, false, true, 'transport', 'Texnik ko''rik oldi xizmat ishlari belgilandi', NULL),
        (17, 'NAV-WO-2026-0017', 'NAV-RR-2026-0017', NULL, NULL, 'Bortli yuk avtomobilida shina holatini tekshirish', 'INSPECTION', 'PLANNED', 'MEDIUM', false, false, false, 'transport', 'Shina yeyilishi bo''yicha ko''rik rejalashtirildi', NULL),
        (18, 'NAV-WO-2026-0018', 'NAV-RR-2026-0018', NULL, NULL, 'Tezkor javob avtomobili yoritish zanjiri ko''rigi', 'INSPECTION', 'APPROVED', 'HIGH', false, false, false, 'transport', 'Yoritish zanjiri diagnostikasi tayyorlandi', NULL),
        (19, 'NAV-WO-2026-0019', 'NAV-RR-2026-0019', NULL, NULL, 'Payvandlash servis avtomobili generator diagnostikasi', 'INSPECTION', 'IN_PROGRESS', 'HIGH', false, false, false, 'transport', 'Generator yuklama ostida tekshirilmoqda', NULL),
        (20, 'NAV-WO-2026-0020', 'NAV-RR-2026-0020', NULL, NULL, 'Mobil kompressor bosim regulyatorini sozlash', 'PLANNED', 'CLOSED', 'MEDIUM', false, false, false, 'transport', 'Bosim regulyatori sozlandi', 'Bosim ushlash rejimi barqaror')
    ) AS v(idx, number, rr_number, defect_code, defect_list_code, title, type, status, priority, repair_act_required, stoppage_act_required, uses_materials, performer_group, summary, result)
)
INSERT INTO work_orders (
    id, created_at, updated_at, is_deleted, created_by_id, updated_by_id,
    number, title, equipment_id, location_id, department_id, work_location_note,
    repair_request_id, defect_id, defect_list_id, brigade_member_id, warehouse_id,
    status, type, work_type, priority, start_planned_at, end_planned_at, started_at, completed_at,
    summary, result, repair_act_required, stoppage_act_required, closure_notes, approved_by_id
)
SELECT ('71000000-0000-0000-0000-00000006' || lpad(wp.idx::text, 4, '0'))::uuid,
       now(), now(), false, COALESCE(actor.planner_id, actor.chief_mechanic_id), COALESCE(actor.planner_id, actor.chief_mechanic_id),
       wp.number, wp.title, rr.equipment_id, rr.location_id, rr.department_id, 'Ish joyi uskunaning joriy joylashuvida belgilandi',
       rr.id, d.id, dl.id, bm.id, CASE WHEN wp.uses_materials THEN wh.id ELSE NULL END,
       wp.status, wp.type, 'REPAIR', wp.priority,
       TIMESTAMP '2026-07-02 09:00:00' + ((wp.idx - 1) || ' days')::interval,
       TIMESTAMP '2026-07-02 18:00:00' + ((wp.idx - 1) || ' days')::interval,
       CASE WHEN wp.status IN ('IN_PROGRESS','COMPLETED','CLOSED') THEN TIMESTAMP '2026-07-02 10:00:00' + ((wp.idx - 1) || ' days')::interval ELSE NULL END,
       CASE WHEN wp.status IN ('COMPLETED','CLOSED') THEN TIMESTAMP '2026-07-02 16:00:00' + ((wp.idx - 1) || ' days')::interval ELSE NULL END,
       wp.summary, wp.result, wp.repair_act_required, wp.stoppage_act_required,
       CASE WHEN wp.status = 'CLOSED' THEN 'Ish natijasi qabul qilindi va foydalanishga ruxsat berildi' ELSE NULL END,
       CASE WHEN wp.status IN ('APPROVED','IN_PROGRESS','COMPLETED','CLOSED') THEN actor.chief_mechanic_id ELSE NULL END
FROM wo_plan wp
JOIN repair_requests rr ON rr.number = wp.rr_number AND rr.is_deleted = false
LEFT JOIN defects d ON d.code = wp.defect_code AND d.is_deleted = false
LEFT JOIN defect_lists dl ON dl.code = wp.defect_list_code AND dl.is_deleted = false
LEFT JOIN warehouses wh ON wh.code = 'NAV-WH-MAIN' AND wh.is_deleted = false
CROSS JOIN actor
LEFT JOIN LATERAL (
    SELECT bm.id
    FROM brigade_members bm
    JOIN users u ON u.id = bm.user_id
    JOIN brigades b ON b.id = bm.brigade_id
    WHERE bm.is_deleted = false
      AND bm.is_active = true
      AND (
          (wp.performer_group = 'mechanic' AND b.code = 'NAV-AZOT-BR-MEX')
          OR (wp.performer_group = 'electrical' AND b.code = 'NAV-AZOT-BR-ELEC')
          OR (wp.performer_group = 'transport' AND b.code = 'NAV-AZOT-BR-TR')
      )
    ORDER BY bm.created_at
    LIMIT 1
) bm ON true
ON CONFLICT (number) DO UPDATE
SET title = EXCLUDED.title,
    equipment_id = EXCLUDED.equipment_id,
    location_id = EXCLUDED.location_id,
    department_id = EXCLUDED.department_id,
    work_location_note = EXCLUDED.work_location_note,
    repair_request_id = EXCLUDED.repair_request_id,
    defect_id = EXCLUDED.defect_id,
    defect_list_id = EXCLUDED.defect_list_id,
    brigade_member_id = EXCLUDED.brigade_member_id,
    warehouse_id = EXCLUDED.warehouse_id,
    status = EXCLUDED.status,
    type = EXCLUDED.type,
    work_type = EXCLUDED.work_type,
    priority = EXCLUDED.priority,
    start_planned_at = EXCLUDED.start_planned_at,
    end_planned_at = EXCLUDED.end_planned_at,
    started_at = EXCLUDED.started_at,
    completed_at = EXCLUDED.completed_at,
    summary = EXCLUDED.summary,
    result = EXCLUDED.result,
    repair_act_required = EXCLUDED.repair_act_required,
    stoppage_act_required = EXCLUDED.stoppage_act_required,
    closure_notes = EXCLUDED.closure_notes,
    approved_by_id = EXCLUDED.approved_by_id,
    is_deleted = false,
    updated_by_id = EXCLUDED.updated_by_id,
    updated_at = now();

UPDATE defect_lists dl
SET work_order_id = wo.id,
    updated_at = now()
FROM work_orders wo
WHERE dl.is_deleted = false
  AND wo.is_deleted = false
  AND dl.code = replace(wo.number, 'NAV-WO', 'NAV-DL')
  AND wo.number BETWEEN 'NAV-WO-2026-0001' AND 'NAV-WO-2026-0010';

WITH issue_plan AS (
    SELECT *
    FROM (VALUES
        (1, '71000000-0000-0000-0000-000000070001'::uuid, '71000000-0000-0000-0000-000000080001'::uuid, '71000000-0000-0000-0000-000000090001'::uuid, 'NAV-WO-2026-0001', 'NAV-SP-BRG-6312', 1.0::numeric, 'Podshipnik almashtirish uchun berildi'),
        (2, '71000000-0000-0000-0000-000000070002'::uuid, '71000000-0000-0000-0000-000000080002'::uuid, '71000000-0000-0000-0000-000000090002'::uuid, 'NAV-WO-2026-0001', 'NAV-SP-GREASE-EP2', 2.0::numeric, 'Podshipnik korpusi uchun surtma berildi'),
        (3, '71000000-0000-0000-0000-000000070003'::uuid, '71000000-0000-0000-0000-000000080003'::uuid, '71000000-0000-0000-0000-000000090003'::uuid, 'NAV-WO-2026-0002', 'NAV-SP-SEAL-60', 1.0::numeric, 'Kompressor salnigi uchun berildi'),
        (4, '71000000-0000-0000-0000-000000070004'::uuid, '71000000-0000-0000-0000-000000080004'::uuid, '71000000-0000-0000-0000-000000090004'::uuid, 'NAV-WO-2026-0003', 'NAV-SP-GASKET-DN150', 2.0::numeric, 'Flanes zichligi uchun prokladka berildi'),
        (5, '71000000-0000-0000-0000-000000070005'::uuid, '71000000-0000-0000-0000-000000080005'::uuid, '71000000-0000-0000-0000-000000090005'::uuid, 'NAV-WO-2026-0004', 'NAV-SP-GAUGE-16BAR', 1.0::numeric, 'Nazorat asbobi uchun manometr berildi'),
        (6, '71000000-0000-0000-0000-000000070006'::uuid, '71000000-0000-0000-0000-000000080006'::uuid, '71000000-0000-0000-0000-000000090006'::uuid, 'NAV-WO-2026-0005', 'NAV-SP-BELT-C2240', 2.0::numeric, 'Ventilyator yuritmasi uchun kamar berildi'),
        (7, '71000000-0000-0000-0000-000000070007'::uuid, '71000000-0000-0000-0000-000000080007'::uuid, '71000000-0000-0000-0000-000000090007'::uuid, 'NAV-WO-2026-0006', 'NAV-SP-BRG-6208', 2.0::numeric, 'Elektr dvigatel podshipnigi uchun berildi'),
        (8, '71000000-0000-0000-0000-000000070008'::uuid, '71000000-0000-0000-0000-000000080008'::uuid, '71000000-0000-0000-0000-000000090008'::uuid, 'NAV-WO-2026-0011', 'NAV-SP-BRAKE-PAD', 1.0::numeric, 'Pikap tormoz tizimi uchun berildi'),
        (9, '71000000-0000-0000-0000-000000070009'::uuid, '71000000-0000-0000-0000-000000080009'::uuid, '71000000-0000-0000-0000-000000090009'::uuid, 'NAV-WO-2026-0011', 'NAV-SP-BOLT-M16', 4.0::numeric, 'Mahkamlash komplekti berildi'),
        (10, '71000000-0000-0000-0000-000000070010'::uuid, '71000000-0000-0000-0000-000000080010'::uuid, '71000000-0000-0000-0000-000000090010'::uuid, 'NAV-WO-2026-0012', 'NAV-SP-VEH-OIL-FILTER', 1.0::numeric, 'Servis furgoni moy filtri berildi'),
        (11, '71000000-0000-0000-0000-000000070011'::uuid, '71000000-0000-0000-0000-000000080011'::uuid, '71000000-0000-0000-0000-000000090011'::uuid, 'NAV-WO-2026-0012', 'NAV-SP-OIL-VG46', 8.0::numeric, 'Avtomobil moy almashtirish uchun moy berildi'),
        (12, '71000000-0000-0000-0000-000000070012'::uuid, '71000000-0000-0000-0000-000000080012'::uuid, '71000000-0000-0000-0000-000000090012'::uuid, 'NAV-WO-2026-0013', 'NAV-SP-HOSE-34', 4.0::numeric, 'Avtokran gidravlik konturi uchun shlang berildi'),
        (13, '71000000-0000-0000-0000-000000070013'::uuid, '71000000-0000-0000-0000-000000080013'::uuid, '71000000-0000-0000-0000-000000090013'::uuid, 'NAV-WO-2026-0014', 'NAV-SP-OIL-FILTER', 1.0::numeric, 'Avtoyuklagich moy filtri berildi'),
        (14, '71000000-0000-0000-0000-000000070014'::uuid, '71000000-0000-0000-0000-000000080014'::uuid, '71000000-0000-0000-0000-000000090014'::uuid, 'NAV-WO-2026-0014', 'NAV-SP-AIR-FILTER', 1.0::numeric, 'Avtoyuklagich havo filtri berildi'),
        (15, '71000000-0000-0000-0000-000000070015'::uuid, '71000000-0000-0000-0000-000000080015'::uuid, '71000000-0000-0000-0000-000000090015'::uuid, 'NAV-WO-2026-0015', 'NAV-SP-HOSE-12', 3.0::numeric, 'Yoqilgi servis nasosi uchun shlang berildi'),
        (16, '71000000-0000-0000-0000-000000070016'::uuid, '71000000-0000-0000-0000-000000080016'::uuid, '71000000-0000-0000-0000-000000090016'::uuid, 'NAV-WO-2026-0015', 'NAV-SP-DIESEL-FILTER', 1.0::numeric, 'Dizel yoqilgi filtri berildi'),
        (17, '71000000-0000-0000-0000-000000070017'::uuid, '71000000-0000-0000-0000-000000080017'::uuid, '71000000-0000-0000-0000-000000090017'::uuid, 'NAV-WO-2026-0016', 'NAV-SP-DIESEL-FILTER', 1.0::numeric, 'Avtobus texnik xizmatiga filtr berildi'),
        (18, '71000000-0000-0000-0000-000000070018'::uuid, '71000000-0000-0000-0000-000000080018'::uuid, '71000000-0000-0000-0000-000000090018'::uuid, 'NAV-WO-2026-0016', 'NAV-SP-BOLT-M16', 6.0::numeric, 'Mahkamlash komplekti berildi')
    ) AS v(seq, usage_id, metadata_id, ledger_id, wo_number, spare_part_code, quantity, notes)
),
resolved_plan AS (
    SELECT ip.*, wo.id AS work_order_id, wo.department_id, wh.id AS warehouse_id, sp.id AS spare_part_id,
           COALESCE(sp.average_cost, sp.last_purchase_price, 100000)::numeric(19,4) AS unit_cost,
           COALESCE(wo.approved_by_id, wo.created_by_id) AS issued_by_id
    FROM issue_plan ip
    JOIN work_orders wo ON wo.number = ip.wo_number AND wo.is_deleted = false
    JOIN warehouses wh ON wh.code = 'NAV-WH-MAIN' AND wh.is_deleted = false
    JOIN spare_parts sp ON sp.code = ip.spare_part_code AND sp.is_deleted = false
),
inserted_metadata AS (
    INSERT INTO warehouse_stock_ledger_metadata (
        id, created_at, updated_at, is_deleted, created_by_id, updated_by_id,
        warehouse_id, spare_part_id, work_order_id, legacy_type, submitted_quantity, unit,
        submitted_unit_cost, unit_price, submitted_total_amount, document_number,
        source_type, source_id, responsible_person_id, taken_by_id, department_id,
        movement_date, submitted_occurred_at, notes, comment,
        stock_status, source_document_no
    )
    SELECT rp.metadata_id, now(), now(), false, rp.issued_by_id, rp.issued_by_id,
           rp.warehouse_id, rp.spare_part_id, rp.work_order_id, 'ISSUE', rp.quantity::double precision, sp.unit,
           rp.unit_cost::double precision, rp.unit_cost::numeric(19,2), (rp.quantity * rp.unit_cost)::numeric(19,2), rp.wo_number,
           'WORK_ORDER_MATERIAL_USAGE', rp.work_order_id, rp.issued_by_id, rp.issued_by_id, rp.department_id,
           DATE '2026-07-20' + (rp.seq - 1), TIMESTAMP '2026-07-20 10:00:00' + ((rp.seq - 1) || ' hours')::interval,
           rp.notes, 'Material ombordan ish buyrug''i uchun chiqarildi', 'AVAILABLE', rp.wo_number
    FROM resolved_plan rp
    JOIN spare_parts sp ON sp.id = rp.spare_part_id
    ON CONFLICT (id) DO UPDATE
    SET warehouse_id = EXCLUDED.warehouse_id,
        spare_part_id = EXCLUDED.spare_part_id,
        work_order_id = EXCLUDED.work_order_id,
        submitted_quantity = EXCLUDED.submitted_quantity,
        submitted_unit_cost = EXCLUDED.submitted_unit_cost,
        submitted_total_amount = EXCLUDED.submitted_total_amount,
        document_number = EXCLUDED.document_number,
        source_type = EXCLUDED.source_type,
        source_id = EXCLUDED.source_id,
        responsible_person_id = EXCLUDED.responsible_person_id,
        taken_by_id = EXCLUDED.taken_by_id,
        department_id = EXCLUDED.department_id,
        movement_date = EXCLUDED.movement_date,
        submitted_occurred_at = EXCLUDED.submitted_occurred_at,
        notes = EXCLUDED.notes,
        comment = EXCLUDED.comment,
        stock_status = EXCLUDED.stock_status,
        source_document_no = EXCLUDED.source_document_no,
        is_deleted = false,
        updated_at = now()
    RETURNING id
),
upserted_usage AS (
    INSERT INTO repair_material_usages (
        id, created_at, updated_at, is_deleted,
        work_order_id, warehouse_id, spare_part_id, stock_movement_id,
        issued_by_id, issued_at, quantity, unit_cost, notes, stock_status, source_document_no
    )
    SELECT rp.usage_id, now(), now(), false,
           rp.work_order_id, rp.warehouse_id, rp.spare_part_id, rp.metadata_id,
           rp.issued_by_id, TIMESTAMP '2026-07-20 10:30:00' + ((rp.seq - 1) || ' hours')::interval,
           rp.quantity::double precision, rp.unit_cost::double precision, rp.notes, 'AVAILABLE', rp.wo_number
    FROM resolved_plan rp
    ON CONFLICT (id) DO UPDATE
    SET work_order_id = EXCLUDED.work_order_id,
        warehouse_id = EXCLUDED.warehouse_id,
        spare_part_id = EXCLUDED.spare_part_id,
        stock_movement_id = EXCLUDED.stock_movement_id,
        issued_by_id = EXCLUDED.issued_by_id,
        issued_at = EXCLUDED.issued_at,
        quantity = EXCLUDED.quantity,
        unit_cost = EXCLUDED.unit_cost,
        notes = EXCLUDED.notes,
        stock_status = EXCLUDED.stock_status,
        source_document_no = EXCLUDED.source_document_no,
        is_deleted = false,
        updated_at = now()
    RETURNING id
),
inserted_ledgers AS (
    INSERT INTO warehouse_stock_ledgers (
        id, created_at, updated_at, is_deleted,
        warehouse_id, spare_part_id, movement_type, quantity, unit_cost, total_cost,
        reference_type, reference_id, reference_doc_no, idempotency_key, posted_at, notes, stock_status
    )
    SELECT rp.ledger_id, now(), now(), false,
           rp.warehouse_id, rp.spare_part_id, 'ISSUE', -rp.quantity, rp.unit_cost, (rp.quantity * rp.unit_cost)::numeric(19,2),
           'WORK_ORDER', rp.work_order_id, rp.wo_number,
           'NAV-WMS-ISSUE-' || rp.wo_number || '-' || rp.spare_part_code,
           TIMESTAMP '2026-07-20 11:00:00' + ((rp.seq - 1) || ' hours')::interval,
           rp.notes, 'AVAILABLE'
    FROM resolved_plan rp
    ON CONFLICT (idempotency_key) WHERE idempotency_key IS NOT NULL AND is_deleted = false DO NOTHING
    RETURNING warehouse_id, spare_part_id, stock_status, abs(quantity) AS issued_qty
),
issue_totals AS (
    SELECT warehouse_id, spare_part_id, stock_status, sum(issued_qty) AS issued_qty
    FROM inserted_ledgers
    GROUP BY warehouse_id, spare_part_id, stock_status
)
UPDATE warehouse_stock_balances b
SET qty_on_hand = b.qty_on_hand - it.issued_qty,
    updated_at = now()
FROM issue_totals it
WHERE b.warehouse_id = it.warehouse_id
  AND b.spare_part_id = it.spare_part_id
  AND b.stock_status = it.stock_status
  AND b.is_deleted = false
  AND b.bin_id IS NULL
  AND b.qty_on_hand - b.qty_reserved >= it.issued_qty;
