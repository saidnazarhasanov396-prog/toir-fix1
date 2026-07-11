INSERT INTO equipment_types (id, created_at, updated_at, is_deleted, code, name, name_en, name_uz, category, description)
VALUES
('71000000-0000-0000-0000-000000000801', now(), now(), false, 'NAV-ET-PUMP', 'Sanoat nasosi', 'Industrial pump', 'Sanoat nasosi', 'PUMP', 'Texnologik nasos agregatlari'),
('71000000-0000-0000-0000-000000000802', now(), now(), false, 'NAV-ET-COMPRESSOR', 'Markazdan qochma kompressor', 'Centrifugal compressor', 'Markazdan qochma kompressor', 'MECHANICAL', 'Kompressor zali asosiy agregatlari'),
('71000000-0000-0000-0000-000000000803', now(), now(), false, 'NAV-ET-HEAT-EXCHANGER', 'Issiqlik almashinish apparati', 'Heat exchanger', 'Issiqlik almashinish apparati', 'MECHANICAL', 'Karbamid va yordamchi tizimlar issiqlik almashinish uskunalari'),
('71000000-0000-0000-0000-000000000804', now(), now(), false, 'NAV-ET-REACTOR', 'Texnologik reaktor', 'Process reactor', 'Texnologik reaktor', 'MECHANICAL', 'Sintez va yordamchi reaktor uzellari'),
('71000000-0000-0000-0000-000000000805', now(), now(), false, 'NAV-ET-FAN', 'Sanoat ventilyatori', 'Industrial fan', 'Sanoat ventilyatori', 'MECHANICAL', 'Sovitish va ventilyatsiya agregatlari'),
('71000000-0000-0000-0000-000000000806', now(), now(), false, 'NAV-ET-MOTOR', 'Elektr dvigatel', 'Electric motor', 'Elektr dvigatel', 'ELECTRICAL', 'Yuqori quvvatli elektr yuritmalar'),
('71000000-0000-0000-0000-000000000807', now(), now(), false, 'NAV-ET-INSTRUMENT-CABINET', 'NQOA boshqaruv shkafi', 'Instrumentation control cabinet', 'NQOA boshqaruv shkafi', 'INSTRUMENT', 'Avtomatika va o''lchov konturlari shkaflari'),
('71000000-0000-0000-0000-000000000808', now(), now(), false, 'NAV-ET-GENERATOR', 'Dizel generator', 'Diesel generator', 'Dizel generator', 'ELECTRICAL', 'Zaxira elektr ta''minoti uskunalari'),
('71000000-0000-0000-0000-000000000809', now(), now(), false, 'NAV-ET-PIPELINE', 'Texnologik quvur liniyasi', 'Process pipeline', 'Texnologik quvur liniyasi', 'MECHANICAL', 'Azot kislotasi va yordamchi texnologik quvurlar'),
('71000000-0000-0000-0000-000000000810', now(), now(), false, 'NAV-ET-VEHICLE-TRUCK', 'Yuk transporti', 'Truck vehicle', 'Yuk transporti', 'OTHER', 'Korxona yuk va servis transportlari'),
('71000000-0000-0000-0000-000000000811', now(), now(), false, 'NAV-ET-VEHICLE-SPECIAL', 'Maxsus texnika', 'Special vehicle', 'Maxsus texnika', 'OTHER', 'Kran, yuklagich va maxsus servis texnikasi'),
('71000000-0000-0000-0000-000000000812', now(), now(), false, 'NAV-ET-VEHICLE-PASSENGER', 'Xizmat yengil transporti', 'Passenger service vehicle', 'Xizmat yengil transporti', 'OTHER', 'Xizmat va tezkor javob transportlari')
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    name_en = EXCLUDED.name_en,
    name_uz = EXCLUDED.name_uz,
    category = EXCLUDED.category,
    description = EXCLUDED.description,
    is_deleted = false,
    updated_at = now();

INSERT INTO equipment (
    id, created_at, updated_at, is_deleted,
    code, name, inventory_number, technical_number, serial_number, model, produced_year,
    equipment_type_id, department_id, location_id, current_location_type, current_warehouse_id,
    responsible_department_id, responsible_id, manufacturer, status, category,
    commissioned_at, arrival_date, warranty_until, has_warranty, warranty_start_date, warranty_end_date,
    average_operating_life_hours, average_daily_usage, operation_start_date,
    expected_lifetime_months, expected_lifetime_years, expected_lifetime_hours,
    description
)
SELECT v.id, now(), now(), false,
       v.code, v.name, v.inventory_number, v.technical_number, v.serial_number, v.model, v.produced_year,
       et.id, d.id, l.id, 'DEPARTMENT', NULL,
       d.id, responsible_employee.id, v.manufacturer, v.status, 'PRODUCTION_EQUIPMENT',
       v.commissioned_at, v.arrival_date, v.warranty_until, v.has_warranty, v.warranty_start_date, v.warranty_end_date,
       v.average_operating_life_hours, v.average_daily_usage, v.operation_start_date,
       v.expected_lifetime_months, v.expected_lifetime_years, v.expected_lifetime_hours,
       v.description
FROM (VALUES
    ('71000000-0000-0000-0000-000000000901'::uuid, 'NAV-EQ-PUMP-001', 'Ammiak sexi sirkulyatsion nasos agregati', 'NAV-INV-EQ-0001', 'NAV-TN-EQ-0001', 'NAV-SN-PUMP-0001', 'NK-200-150', 2021, 'NAV-ET-PUMP', 'NAV-AZOT-AMM', 'NAV-LOC-AMM-PUMP', 'nav.azot.mech.foreman', 'UzPump', 'ACTIVE', DATE '2021-03-10', DATE '2021-02-25', true, DATE '2021-03-10', DATE '2027-03-09', DATE '2027-03-09', 72000::bigint, 18.5::double precision, DATE '2021-03-15', 144, 12, 72000::bigint, 'Ammiak sexi texnologik konturidagi asosiy sirkulyatsion nasos'),
    ('71000000-0000-0000-0000-000000000902'::uuid, 'NAV-EQ-PUMP-002', 'Ammiak sexi zaxira nasos agregati', 'NAV-INV-EQ-0002', 'NAV-TN-EQ-0002', 'NAV-SN-PUMP-0002', 'NK-160-120', 2020, 'NAV-ET-PUMP', 'NAV-AZOT-AMM', 'NAV-LOC-AMM-PUMP', 'nav.azot.mech.foreman', 'UzPump', 'STANDBY', DATE '2020-08-18', DATE '2020-08-01', true, DATE '2020-08-18', DATE '2026-08-17', DATE '2026-08-17', 68000::bigint, 6.0::double precision, DATE '2020-08-20', 144, 12, 68000::bigint, 'Avariya holatida ishga tushiriladigan zaxira nasos agregati'),
    ('71000000-0000-0000-0000-000000000903'::uuid, 'NAV-EQ-COMP-001', 'Kompressor zali markazdan qochma kompressori', 'NAV-INV-EQ-0003', 'NAV-TN-EQ-0003', 'NAV-SN-COMP-0001', 'K-5200', 2019, 'NAV-ET-COMPRESSOR', 'NAV-AZOT-AMM', 'NAV-LOC-AMM-COMP', 'nav.azot.chief.mechanic', 'Siemens Energy', 'ACTIVE', DATE '2019-11-04', DATE '2019-10-12', true, DATE '2019-11-04', DATE '2026-11-03', DATE '2026-11-03', 96000::bigint, 22.0::double precision, DATE '2019-11-08', 180, 15, 96000::bigint, 'Kompressor zali yuqori unumdorlikdagi markazdan qochma kompressori'),
    ('71000000-0000-0000-0000-000000000904'::uuid, 'NAV-EQ-HE-001', 'Karbamid bloki issiqlik almashinish apparati', 'NAV-INV-EQ-0004', 'NAV-TN-EQ-0004', 'NAV-SN-HE-0001', 'TEMA-BEM-180', 2022, 'NAV-ET-HEAT-EXCHANGER', 'NAV-AZOT-KARB', 'NAV-LOC-KARB-REACTOR', 'nav.azot.planner', 'UzKimyoMash', 'ACTIVE', DATE '2022-05-16', DATE '2022-04-30', true, DATE '2022-05-16', DATE '2028-05-15', DATE '2028-05-15', 80000::bigint, 16.0::double precision, DATE '2022-05-20', 144, 12, 80000::bigint, 'Karbamid sintez blokida harorat rejimini ushlab turuvchi apparat'),
    ('71000000-0000-0000-0000-000000000905'::uuid, 'NAV-EQ-REACTOR-001', 'Karbamid sintez reaktori yordamchi uzeli', 'NAV-INV-EQ-0005', 'NAV-TN-EQ-0005', 'NAV-SN-REACTOR-0001', 'R-3100-A', 2018, 'NAV-ET-REACTOR', 'NAV-AZOT-KARB', 'NAV-LOC-KARB-REACTOR', 'nav.azot.chief.mechanic', 'Navoiy Kimyo Qurilma', 'ACTIVE', DATE '2018-09-03', DATE '2018-08-12', false, NULL::date, NULL::date, NULL::date, 110000::bigint, 20.0::double precision, DATE '2018-09-06', 216, 18, 110000::bigint, 'Karbamid sintez jarayonidagi yordamchi reaktor uzeli'),
    ('71000000-0000-0000-0000-000000000906'::uuid, 'NAV-EQ-FAN-001', 'Sovutish minorasi ventilyator agregati', 'NAV-INV-EQ-0006', 'NAV-TN-EQ-0006', 'NAV-SN-FAN-0001', 'CTF-1250', 2017, 'NAV-ET-FAN', 'NAV-AZOT-KARB', 'NAV-LOC-KARB-GRAN', 'nav.azot.mech.foreman', 'Howden', 'IN_REPAIR', DATE '2017-06-14', DATE '2017-05-29', false, NULL::date, NULL::date, NULL::date, 62000::bigint, 14.0::double precision, DATE '2017-06-18', 120, 10, 62000::bigint, 'Sovitish minorasi havo oqimini ta''minlovchi ventilyator agregati'),
    ('71000000-0000-0000-0000-000000000907'::uuid, 'NAV-EQ-MOTOR-001', 'Elektr dvigatel 160 kW', 'NAV-INV-EQ-0007', 'NAV-TN-EQ-0007', 'NAV-SN-MOTOR-0001', '4A315M4', 2020, 'NAV-ET-MOTOR', 'NAV-AZOT-ELEC', 'NAV-LOC-ELEC', 'nav.azot.elec.foreman', 'Uzelektromotor', 'ACTIVE', DATE '2020-02-12', DATE '2020-01-25', false, NULL::date, NULL::date, NULL::date, 70000::bigint, 12.0::double precision, DATE '2020-02-14', 132, 11, 70000::bigint, 'Texnologik nasos yuritmasi uchun 160 kW elektr dvigatel'),
    ('71000000-0000-0000-0000-000000000908'::uuid, 'NAV-EQ-CABINET-001', 'NQO''A boshqaruv shkafi', 'NAV-INV-EQ-0008', 'NAV-TN-EQ-0008', 'NAV-SN-CABINET-0001', 'PLC-S7-1500', 2021, 'NAV-ET-INSTRUMENT-CABINET', 'NAV-AZOT-ELEC', 'NAV-LOC-ELEC', 'nav.azot.elec.foreman', 'Siemens', 'ACTIVE', DATE '2021-10-05', DATE '2021-09-15', false, NULL::date, NULL::date, NULL::date, 60000::bigint, 9.0::double precision, DATE '2021-10-08', 120, 10, 60000::bigint, 'NQOA signallari va himoya algoritmlarini boshqaruvchi shkaf'),
    ('71000000-0000-0000-0000-000000000909'::uuid, 'NAV-EQ-GEN-001', 'Favqulodda dizel generator', 'NAV-INV-EQ-0009', 'NAV-TN-EQ-0009', 'NAV-SN-GEN-0001', 'DG-800', 2016, 'NAV-ET-GENERATOR', 'NAV-AZOT-ELEC', 'NAV-LOC-ELEC', 'nav.azot.elec.foreman', 'Cummins', 'STANDBY', DATE '2016-12-22', DATE '2016-12-01', false, NULL::date, NULL::date, NULL::date, 50000::bigint, 2.0::double precision, DATE '2016-12-25', 180, 15, 50000::bigint, 'Favqulodda elektr ta''minoti uchun dizel generator agregati'),
    ('71000000-0000-0000-0000-000000000910'::uuid, 'NAV-EQ-PIPE-001', 'Azot kislotasi quvur liniyasi uchastkasi', 'NAV-INV-EQ-0010', 'NAV-TN-EQ-0010', 'NAV-SN-PIPE-0001', 'DN150-PN16', 2018, 'NAV-ET-PIPELINE', 'NAV-AZOT-AK', 'NAV-LOC-AK-LINE', 'nav.azot.planner', 'UzKimyoMontaj', 'ACTIVE', DATE '2018-04-09', DATE '2018-03-18', false, NULL::date, NULL::date, NULL::date, 90000::bigint, 18.0::double precision, DATE '2018-04-12', 180, 15, 90000::bigint, 'Azot kislotasi texnologik liniyasidagi DN150 quvur uchastkasi')
) AS v(id, code, name, inventory_number, technical_number, serial_number, model, produced_year, equipment_type_code, department_code, location_code, responsible_username, manufacturer, status, commissioned_at, arrival_date, has_warranty, warranty_start_date, warranty_end_date, warranty_until, average_operating_life_hours, average_daily_usage, operation_start_date, expected_lifetime_months, expected_lifetime_years, expected_lifetime_hours, description)
JOIN equipment_types et ON et.code = v.equipment_type_code AND et.is_deleted = false
JOIN departments d ON d.code = v.department_code AND d.is_deleted = false
JOIN locations l ON l.code = v.location_code AND l.is_deleted = false
LEFT JOIN users u ON u.username = v.responsible_username AND u.is_deleted = false
LEFT JOIN LATERAL (
    SELECT min(e.id) AS id FROM hr_employees e
    WHERE e.user_id = u.id AND e.is_deleted = false
    HAVING count(*) = 1
) responsible_employee ON true
ON CONFLICT (code) WHERE is_deleted = false DO UPDATE
SET name = EXCLUDED.name,
    inventory_number = EXCLUDED.inventory_number,
    technical_number = EXCLUDED.technical_number,
    serial_number = EXCLUDED.serial_number,
    model = EXCLUDED.model,
    produced_year = EXCLUDED.produced_year,
    equipment_type_id = EXCLUDED.equipment_type_id,
    department_id = EXCLUDED.department_id,
    location_id = EXCLUDED.location_id,
    current_location_type = EXCLUDED.current_location_type,
    current_warehouse_id = EXCLUDED.current_warehouse_id,
    responsible_department_id = EXCLUDED.responsible_department_id,
    responsible_id = COALESCE(EXCLUDED.responsible_id, equipment.responsible_id),
    manufacturer = EXCLUDED.manufacturer,
    status = EXCLUDED.status,
    category = EXCLUDED.category,
    commissioned_at = EXCLUDED.commissioned_at,
    arrival_date = EXCLUDED.arrival_date,
    warranty_until = EXCLUDED.warranty_until,
    has_warranty = EXCLUDED.has_warranty,
    warranty_start_date = EXCLUDED.warranty_start_date,
    warranty_end_date = EXCLUDED.warranty_end_date,
    average_operating_life_hours = EXCLUDED.average_operating_life_hours,
    average_daily_usage = EXCLUDED.average_daily_usage,
    operation_start_date = EXCLUDED.operation_start_date,
    expected_lifetime_months = EXCLUDED.expected_lifetime_months,
    expected_lifetime_years = EXCLUDED.expected_lifetime_years,
    expected_lifetime_hours = EXCLUDED.expected_lifetime_hours,
    description = EXCLUDED.description,
    updated_at = now();

INSERT INTO equipment (
    id, created_at, updated_at, is_deleted,
    code, name, inventory_number, technical_number, serial_number, model, produced_year,
    equipment_type_id, department_id, location_id, current_location_type, current_warehouse_id,
    responsible_department_id, responsible_id, manufacturer, status, category,
    commissioned_at, arrival_date, warranty_until, has_warranty, warranty_start_date, warranty_end_date,
    average_operating_life_hours, average_daily_usage, operation_start_date,
    expected_lifetime_months, expected_lifetime_years, expected_lifetime_hours,
    description
)
SELECT v.id, now(), now(), false,
       v.code, v.name, v.inventory_number, v.technical_number, v.serial_number, v.model, v.produced_year,
       et.id, d.id, l.id, 'DEPARTMENT', NULL,
       d.id, e.id, v.manufacturer, v.status, 'VEHICLE',
       v.commissioned_at, v.arrival_date, v.warranty_until, v.has_warranty, v.warranty_start_date, v.warranty_end_date,
       v.average_operating_life_hours, v.average_daily_usage, v.operation_start_date,
       v.expected_lifetime_months, v.expected_lifetime_years, v.expected_lifetime_hours,
       v.description
FROM (VALUES
    ('71000000-0000-0000-0000-000000001001'::uuid, 'NAV-VEH-PICKUP-001', 'Toyota Hilux xizmat pikapi', 'NAV-INV-VEH-0001', 'NAV-TN-VEH-0001', 'NAV-VIN-PICKUP-0001', 'Hilux 2.8D', 2023, 'NAV-ET-VEHICLE-PASSENGER', 'NAV-AZOT-TR', 'NAV-LOC-TRANSPORT', 'NAV-AZOT-EMP-016', 'Toyota', 'ACTIVE', DATE '2023-02-10', DATE '2023-02-01', true, DATE '2023-02-10', DATE '2028-02-09', DATE '2028-02-09', 12000::bigint, 110.0::double precision, DATE '2023-02-12', 96, 8, 12000::bigint, 'Sexlararo tezkor xizmat qatnovlari uchun pikap'),
    ('71000000-0000-0000-0000-000000001002'::uuid, 'NAV-VEH-VAN-001', 'GAZel ta''mirlash furgoni', 'NAV-INV-VEH-0002', 'NAV-TN-VEH-0002', 'NAV-VIN-VAN-0001', 'Next A31R32', 2021, 'NAV-ET-VEHICLE-TRUCK', 'NAV-AZOT-TR', 'NAV-LOC-TRANSPORT', 'NAV-AZOT-EMP-017', 'GAZ', 'ACTIVE', DATE '2021-06-15', DATE '2021-06-01', false, NULL::date, NULL::date, NULL::date, 15000::bigint, 95.0::double precision, DATE '2021-06-18', 96, 8, 15000::bigint, 'Mexanik va elektr brigadalarining joyiga chiqish furgoni'),
    ('71000000-0000-0000-0000-000000001003'::uuid, 'NAV-VEH-CRANE-001', 'XCMG 25 tonna avtokran', 'NAV-INV-VEH-0003', 'NAV-TN-VEH-0003', 'NAV-VIN-CRANE-0001', 'QY25K5C', 2020, 'NAV-ET-VEHICLE-SPECIAL', 'NAV-AZOT-TR', 'NAV-LOC-TRANSPORT', 'NAV-AZOT-EMP-018', 'XCMG', 'ACTIVE', DATE '2020-09-20', DATE '2020-09-05', false, NULL::date, NULL::date, NULL::date, 18000::bigint, 55.0::double precision, DATE '2020-09-23', 120, 10, 18000::bigint, 'Og''ir uskuna va quvur uzellarini ko''tarish uchun avtokran'),
    ('71000000-0000-0000-0000-000000001004'::uuid, 'NAV-VEH-FORKLIFT-001', 'Toyota 3 tonna avtoyuklagich', 'NAV-INV-VEH-0004', 'NAV-TN-VEH-0004', 'NAV-VIN-FORK-0001', '8FD30', 2022, 'NAV-ET-VEHICLE-SPECIAL', 'NAV-AZOT-TR', 'NAV-LOC-TRANSPORT', 'NAV-AZOT-EMP-015', 'Toyota', 'ACTIVE', DATE '2022-04-25', DATE '2022-04-10', true, DATE '2022-04-25', DATE '2027-04-24', DATE '2027-04-24', 10000::bigint, 40.0::double precision, DATE '2022-04-27', 84, 7, 10000::bigint, 'Markaziy ombor va sexlararo yuk tashish uchun avtoyuklagich'),
    ('71000000-0000-0000-0000-000000001005'::uuid, 'NAV-VEH-FUEL-001', 'ISUZU yoqilgi xizmat yuk mashinasi', 'NAV-INV-VEH-0005', 'NAV-TN-VEH-0005', 'NAV-VIN-FUEL-0001', 'NPR Fuel Service', 2019, 'NAV-ET-VEHICLE-TRUCK', 'NAV-AZOT-TR', 'NAV-LOC-TRANSPORT', 'NAV-AZOT-EMP-005', 'ISUZU', 'STANDBY', DATE '2019-12-12', DATE '2019-11-28', false, NULL::date, NULL::date, NULL::date, 16000::bigint, 65.0::double precision, DATE '2019-12-15', 120, 10, 16000::bigint, 'Dizel texnikalarni ichki hududda yoqilgi bilan ta''minlash transporti'),
    ('71000000-0000-0000-0000-000000001006'::uuid, 'NAV-VEH-BUS-001', 'Hyundai County xodimlar avtobusi', 'NAV-INV-VEH-0006', 'NAV-TN-VEH-0006', 'NAV-VIN-BUS-0001', 'County Long', 2018, 'NAV-ET-VEHICLE-PASSENGER', 'NAV-AZOT-TR', 'NAV-LOC-TRANSPORT', NULL, 'Hyundai', 'ACTIVE', DATE '2018-07-19', DATE '2018-07-01', false, NULL::date, NULL::date, NULL::date, 20000::bigint, 130.0::double precision, DATE '2018-07-22', 120, 10, 20000::bigint, 'Navbatchi xodimlarni ishlab chiqarish maydoniga tashish avtobusi'),
    ('71000000-0000-0000-0000-000000001007'::uuid, 'NAV-VEH-FLATBED-001', 'KAMAZ bortli yuk mashinasi', 'NAV-INV-VEH-0007', 'NAV-TN-VEH-0007', 'NAV-VIN-FLATBED-0001', '65117', 2017, 'NAV-ET-VEHICLE-TRUCK', 'NAV-AZOT-TR', 'NAV-LOC-TRANSPORT', NULL, 'KAMAZ', 'ACTIVE', DATE '2017-10-09', DATE '2017-09-20', false, NULL::date, NULL::date, NULL::date, 22000::bigint, 80.0::double precision, DATE '2017-10-12', 132, 11, 22000::bigint, 'Metall konstruksiya va yirik ehtiyot qismlarni tashish transporti'),
    ('71000000-0000-0000-0000-000000001008'::uuid, 'NAV-VEH-EMERGENCY-001', 'UAZ tezkor javob avtomobili', 'NAV-INV-VEH-0008', 'NAV-TN-VEH-0008', 'NAV-VIN-RESP-0001', 'Patriot', 2020, 'NAV-ET-VEHICLE-PASSENGER', 'NAV-AZOT-TR', 'NAV-LOC-TRANSPORT', NULL, 'UAZ', 'ACTIVE', DATE '2020-03-16', DATE '2020-03-01', false, NULL::date, NULL::date, NULL::date, 14000::bigint, 75.0::double precision, DATE '2020-03-18', 96, 8, 14000::bigint, 'Texnik navbatchilik va tezkor javob guruhi avtomobili'),
    ('71000000-0000-0000-0000-000000001009'::uuid, 'NAV-VEH-WELDING-001', 'MAN payvandlash servis yuk mashinasi', 'NAV-INV-VEH-0009', 'NAV-TN-VEH-0009', 'NAV-VIN-WELD-0001', 'TGM Welding Service', 2021, 'NAV-ET-VEHICLE-SPECIAL', 'NAV-AZOT-TR', 'NAV-LOC-TRANSPORT', NULL, 'MAN', 'IN_REPAIR', DATE '2021-11-22', DATE '2021-11-05', true, DATE '2021-11-22', DATE '2026-11-21', DATE '2026-11-21', 15000::bigint, 45.0::double precision, DATE '2021-11-25', 96, 8, 15000::bigint, 'Joyida payvandlash ishlari uchun jihozlangan servis yuk mashinasi'),
    ('71000000-0000-0000-0000-000000001010'::uuid, 'NAV-VEH-COMPRESSOR-TRAILER-001', 'Ko''chma kompressor tirkamasi', 'NAV-INV-VEH-0010', 'NAV-TN-VEH-0010', 'NAV-VIN-TRAILER-0001', 'Atlas Copco XAS', 2022, 'NAV-ET-VEHICLE-SPECIAL', 'NAV-AZOT-TR', 'NAV-LOC-TRANSPORT', NULL, 'Atlas Copco', 'STANDBY', DATE '2022-08-04', DATE '2022-07-20', true, DATE '2022-08-04', DATE '2027-08-03', DATE '2027-08-03', 9000::bigint, 25.0::double precision, DATE '2022-08-07', 84, 7, 9000::bigint, 'Pnevmatik asboblar va vaqtinchalik havo ta''minoti uchun tirkama')
) AS v(id, code, name, inventory_number, technical_number, serial_number, model, produced_year, equipment_type_code, department_code, location_code, responsible_employee_number, manufacturer, status, commissioned_at, arrival_date, has_warranty, warranty_start_date, warranty_end_date, warranty_until, average_operating_life_hours, average_daily_usage, operation_start_date, expected_lifetime_months, expected_lifetime_years, expected_lifetime_hours, description)
JOIN equipment_types et ON et.code = v.equipment_type_code AND et.is_deleted = false
JOIN departments d ON d.code = v.department_code AND d.is_deleted = false
JOIN locations l ON l.code = v.location_code AND l.is_deleted = false
LEFT JOIN hr_employees e ON e.personnel_number = v.responsible_employee_number AND e.is_deleted = false
ON CONFLICT (code) WHERE is_deleted = false DO UPDATE
SET name = EXCLUDED.name,
    inventory_number = EXCLUDED.inventory_number,
    technical_number = EXCLUDED.technical_number,
    serial_number = EXCLUDED.serial_number,
    model = EXCLUDED.model,
    produced_year = EXCLUDED.produced_year,
    equipment_type_id = EXCLUDED.equipment_type_id,
    department_id = EXCLUDED.department_id,
    location_id = EXCLUDED.location_id,
    current_location_type = EXCLUDED.current_location_type,
    current_warehouse_id = EXCLUDED.current_warehouse_id,
    responsible_department_id = EXCLUDED.responsible_department_id,
    responsible_id = COALESCE(EXCLUDED.responsible_id, equipment.responsible_id),
    manufacturer = EXCLUDED.manufacturer,
    status = EXCLUDED.status,
    category = EXCLUDED.category,
    commissioned_at = EXCLUDED.commissioned_at,
    arrival_date = EXCLUDED.arrival_date,
    warranty_until = EXCLUDED.warranty_until,
    has_warranty = EXCLUDED.has_warranty,
    warranty_start_date = EXCLUDED.warranty_start_date,
    warranty_end_date = EXCLUDED.warranty_end_date,
    average_operating_life_hours = EXCLUDED.average_operating_life_hours,
    average_daily_usage = EXCLUDED.average_daily_usage,
    operation_start_date = EXCLUDED.operation_start_date,
    expected_lifetime_months = EXCLUDED.expected_lifetime_months,
    expected_lifetime_years = EXCLUDED.expected_lifetime_years,
    expected_lifetime_hours = EXCLUDED.expected_lifetime_hours,
    description = EXCLUDED.description,
    updated_at = now();

INSERT INTO vehicle_details (
    id, created_at, updated_at, is_deleted,
    equipment_id, plate_number, plate_type, vin, brand, model, manufacture_year,
    vehicle_type, body_number, chassis_number, engine_number, fuel_type,
    fuel_tank_capacity, carrying_capacity, seat_count, assigned_driver_id,
    assigned_driver_usage_limit_minutes, assigned_driver_assigned_by, assigned_driver_assigned_at,
    current_odometer_km, current_engine_hours,
    registration_certificate_number, insurance_policy_number,
    insurance_expiry_date, technical_inspection_expiry_date, gps_device_id
)
SELECT v.id, now(), now(), false,
       eq.id, v.plate_number, v.plate_type, v.vin, v.brand, v.model, v.manufacture_year,
       v.vehicle_type, v.body_number, v.chassis_number, v.engine_number, v.fuel_type,
       v.fuel_tank_capacity, v.carrying_capacity, v.seat_count, driver.id,
       v.assigned_driver_usage_limit_minutes, assigner.id, CASE WHEN driver.id IS NULL THEN NULL ELSE now() END,
       v.current_odometer_km, v.current_engine_hours,
       v.registration_certificate_number, v.insurance_policy_number,
       v.insurance_expiry_date, v.technical_inspection_expiry_date, v.gps_device_id
FROM (VALUES
    ('71000000-0000-0000-0000-000000001101'::uuid, 'NAV-VEH-PICKUP-001', '85 123 VAA', 'LEGAL_ENTITY', 'NAVVINPICKUP0001', 'Toyota', 'Hilux 2.8D', 2023, 'PASSENGER_CAR', 'BODY-PICKUP-001', 'CHS-PICKUP-001', 'ENG-PICKUP-001', 'DIESEL', 80.0::double precision, 1.0::double precision, 5, 'NAV-AZOT-EMP-016', 720, 48250.0::double precision, 1260.0::double precision, 'REG-NAV-VEH-0001', 'INS-NAV-VEH-0001', DATE '2027-02-09', DATE '2027-01-20', 'GPS-NAV-VEH-0001'),
    ('71000000-0000-0000-0000-000000001102'::uuid, 'NAV-VEH-VAN-001', '85 124 VAA', 'LEGAL_ENTITY', 'NAVVINVAN0001', 'GAZ', 'Next A31R32', 2021, 'TRUCK', 'BODY-VAN-001', 'CHS-VAN-001', 'ENG-VAN-001', 'GASOLINE', 64.0::double precision, 1.5::double precision, 3, 'NAV-AZOT-EMP-017', 720, 68540.0::double precision, 2180.0::double precision, 'REG-NAV-VEH-0002', 'INS-NAV-VEH-0002', DATE '2026-08-30', DATE '2026-07-15', 'GPS-NAV-VEH-0002'),
    ('71000000-0000-0000-0000-000000001103'::uuid, 'NAV-VEH-CRANE-001', '85 125 VAA', 'LEGAL_ENTITY', 'NAVVINCRANE0001', 'XCMG', 'QY25K5C', 2020, 'SPECIAL_EQUIPMENT', 'BODY-CRANE-001', 'CHS-CRANE-001', 'ENG-CRANE-001', 'DIESEL', 260.0::double precision, 25.0::double precision, 2, 'NAV-AZOT-EMP-018', 600, 24580.0::double precision, 5350.0::double precision, 'REG-NAV-VEH-0003', 'INS-NAV-VEH-0003', DATE '2026-12-10', DATE '2026-10-05', 'GPS-NAV-VEH-0003'),
    ('71000000-0000-0000-0000-000000001104'::uuid, 'NAV-VEH-FORKLIFT-001', '85 126 VAA', 'LEGAL_ENTITY', 'NAVVINFORK0001', 'Toyota', '8FD30', 2022, 'SPECIAL_EQUIPMENT', 'BODY-FORK-001', 'CHS-FORK-001', 'ENG-FORK-001', 'DIESEL', 70.0::double precision, 3.0::double precision, 1, 'NAV-AZOT-EMP-015', 480, 8100.0::double precision, 3910.0::double precision, 'REG-NAV-VEH-0004', 'INS-NAV-VEH-0004', DATE '2027-04-24', DATE '2027-03-18', 'GPS-NAV-VEH-0004'),
    ('71000000-0000-0000-0000-000000001105'::uuid, 'NAV-VEH-FUEL-001', '85 127 VAA', 'LEGAL_ENTITY', 'NAVVINFUEL0001', 'ISUZU', 'NPR Fuel Service', 2019, 'TRUCK', 'BODY-FUEL-001', 'CHS-FUEL-001', 'ENG-FUEL-001', 'DIESEL', 120.0::double precision, 4.0::double precision, 2, 'NAV-AZOT-EMP-005', 600, 39200.0::double precision, 2760.0::double precision, 'REG-NAV-VEH-0005', 'INS-NAV-VEH-0005', DATE '2026-11-22', DATE '2026-09-28', 'GPS-NAV-VEH-0005'),
    ('71000000-0000-0000-0000-000000001106'::uuid, 'NAV-VEH-BUS-001', '85 128 VAA', 'LEGAL_ENTITY', 'NAVVINBUS0001', 'Hyundai', 'County Long', 2018, 'OTHER', 'BODY-BUS-001', 'CHS-BUS-001', 'ENG-BUS-001', 'DIESEL', 95.0::double precision, 2.0::double precision, 28, NULL, NULL::integer, 112600.0::double precision, 4860.0::double precision, 'REG-NAV-VEH-0006', 'INS-NAV-VEH-0006', DATE '2026-10-17', DATE '2026-08-22', 'GPS-NAV-VEH-0006'),
    ('71000000-0000-0000-0000-000000001107'::uuid, 'NAV-VEH-FLATBED-001', '85 129 VAA', 'LEGAL_ENTITY', 'NAVVINFLATBED0001', 'KAMAZ', '65117', 2017, 'TRUCK', 'BODY-FLATBED-001', 'CHS-FLATBED-001', 'ENG-FLATBED-001', 'DIESEL', 350.0::double precision, 14.0::double precision, 3, NULL, NULL::integer, 87500.0::double precision, 6420.0::double precision, 'REG-NAV-VEH-0007', 'INS-NAV-VEH-0007', DATE '2026-09-14', DATE '2026-07-30', 'GPS-NAV-VEH-0007'),
    ('71000000-0000-0000-0000-000000001108'::uuid, 'NAV-VEH-EMERGENCY-001', '85 130 VAA', 'GOVERNMENT', 'NAVVINRESP0001', 'UAZ', 'Patriot', 2020, 'PASSENGER_CAR', 'BODY-RESP-001', 'CHS-RESP-001', 'ENG-RESP-001', 'GASOLINE', 68.0::double precision, 0.7::double precision, 5, NULL, NULL::integer, 56300.0::double precision, 2015.0::double precision, 'REG-NAV-VEH-0008', 'INS-NAV-VEH-0008', DATE '2026-12-31', DATE '2026-11-12', 'GPS-NAV-VEH-0008'),
    ('71000000-0000-0000-0000-000000001109'::uuid, 'NAV-VEH-WELDING-001', '85 131 VAA', 'LEGAL_ENTITY', 'NAVVINWELD0001', 'MAN', 'TGM Welding Service', 2021, 'SPECIAL_EQUIPMENT', 'BODY-WELD-001', 'CHS-WELD-001', 'ENG-WELD-001', 'DIESEL', 180.0::double precision, 6.0::double precision, 3, NULL, NULL::integer, 41850.0::double precision, 3150.0::double precision, 'REG-NAV-VEH-0009', 'INS-NAV-VEH-0009', DATE '2026-11-21', DATE '2026-10-09', 'GPS-NAV-VEH-0009'),
    ('71000000-0000-0000-0000-000000001110'::uuid, 'NAV-VEH-COMPRESSOR-TRAILER-001', '85 132 VAA', 'TRAILER', 'NAVVINTRAILER0001', 'Atlas Copco', 'XAS', 2022, 'SPECIAL_EQUIPMENT', 'BODY-TRAILER-001', 'CHS-TRAILER-001', 'ENG-TRAILER-001', 'DIESEL', 90.0::double precision, 1.8::double precision, 1, NULL, NULL::integer, 12600.0::double precision, 1890.0::double precision, 'REG-NAV-VEH-0010', 'INS-NAV-VEH-0010', DATE '2027-08-03', DATE '2027-06-26', 'GPS-NAV-VEH-0010')
) AS v(id, equipment_code, plate_number, plate_type, vin, brand, model, manufacture_year, vehicle_type, body_number, chassis_number, engine_number, fuel_type, fuel_tank_capacity, carrying_capacity, seat_count, assigned_driver_number, assigned_driver_usage_limit_minutes, current_odometer_km, current_engine_hours, registration_certificate_number, insurance_policy_number, insurance_expiry_date, technical_inspection_expiry_date, gps_device_id)
JOIN equipment eq ON eq.code = v.equipment_code AND eq.is_deleted = false
LEFT JOIN hr_employees driver ON driver.personnel_number = v.assigned_driver_number AND driver.is_deleted = false
LEFT JOIN users assigner ON assigner.username = 'nav.azot.transport.foreman' AND assigner.is_deleted = false
ON CONFLICT (equipment_id) WHERE is_deleted = false DO UPDATE
SET plate_number = EXCLUDED.plate_number,
    plate_type = EXCLUDED.plate_type,
    vin = EXCLUDED.vin,
    brand = EXCLUDED.brand,
    model = EXCLUDED.model,
    manufacture_year = EXCLUDED.manufacture_year,
    vehicle_type = EXCLUDED.vehicle_type,
    body_number = EXCLUDED.body_number,
    chassis_number = EXCLUDED.chassis_number,
    engine_number = EXCLUDED.engine_number,
    fuel_type = EXCLUDED.fuel_type,
    fuel_tank_capacity = EXCLUDED.fuel_tank_capacity,
    carrying_capacity = EXCLUDED.carrying_capacity,
    seat_count = EXCLUDED.seat_count,
    assigned_driver_id = EXCLUDED.assigned_driver_id,
    assigned_driver_usage_limit_minutes = EXCLUDED.assigned_driver_usage_limit_minutes,
    assigned_driver_assigned_by = EXCLUDED.assigned_driver_assigned_by,
    assigned_driver_assigned_at = EXCLUDED.assigned_driver_assigned_at,
    current_odometer_km = EXCLUDED.current_odometer_km,
    current_engine_hours = EXCLUDED.current_engine_hours,
    registration_certificate_number = EXCLUDED.registration_certificate_number,
    insurance_policy_number = EXCLUDED.insurance_policy_number,
    insurance_expiry_date = EXCLUDED.insurance_expiry_date,
    technical_inspection_expiry_date = EXCLUDED.technical_inspection_expiry_date,
    gps_device_id = EXCLUDED.gps_device_id,
    updated_at = now();
