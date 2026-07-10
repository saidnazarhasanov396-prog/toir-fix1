INSERT INTO users (id, username, email, full_name, password_hash, position, phone, status, department_id, primary_role_id, last_login_at, is_deleted, created_at, updated_at)
SELECT v.id, v.username, v.email, v.full_name,
       '$2a$10$7EqJtq98hPqEX7fNZaFWoOhiNRIK.CuUz42fLJKgPFxTYHTJ8kCzO',
       v.position, v.phone, 'ACTIVE', d.id, r.id, NULL, false, now(), now()
FROM (VALUES
    ('71000000-0000-0000-0000-000000000201'::uuid, 'nav.azot.chief.mechanic', 'chief.mechanic@navoiyazot.uz', 'Bahodir Karimov', 'Zavod bosh mexanigi', '+998790101201', 'NAV-AZOT-MEX', 'CHIEF_MECHANIC'),
    ('71000000-0000-0000-0000-000000000202'::uuid, 'nav.azot.planner', 'planner@navoiyazot.uz', 'Dilshod Tursunov', 'Ta''mirlash rejalashtiruvchisi', '+998790101202', 'NAV-AZOT-MEX', 'PPR_ENGINEER'),
    ('71000000-0000-0000-0000-000000000203'::uuid, 'nav.azot.mech.foreman', 'mechanical.foreman@navoiyazot.uz', 'Sardor Yuldashev', 'Mexanik ta''mirlash brigadiri', '+998790101203', 'NAV-AZOT-MEX', 'FOREMAN'),
    ('71000000-0000-0000-0000-000000000204'::uuid, 'nav.azot.elec.foreman', 'electrical.foreman@navoiyazot.uz', 'Jasur Rakhimov', 'Elektr-NQOA brigadiri', '+998790101204', 'NAV-AZOT-ELEC', 'FOREMAN'),
    ('71000000-0000-0000-0000-000000000205'::uuid, 'nav.azot.transport.foreman', 'transport.foreman@navoiyazot.uz', 'Akmal Saidov', 'Transport ta''mirlash brigadiri', '+998790101205', 'NAV-AZOT-TR', 'FOREMAN'),
    ('71000000-0000-0000-0000-000000000206'::uuid, 'nav.azot.warehouse.head', 'warehouse.head@navoiyazot.uz', 'Gulnora Ismoilova', 'Ombor mudiri', '+998790101206', 'NAV-AZOT-WH', 'STOREKEEPER'),
    ('71000000-0000-0000-0000-000000000207'::uuid, 'nav.azot.storekeeper', 'storekeeper@navoiyazot.uz', 'Nodira Akramova', 'Omborchi', '+998790101207', 'NAV-AZOT-WH', 'STOREKEEPER'),
    ('71000000-0000-0000-0000-000000000208'::uuid, 'nav.azot.economist', 'economist@navoiyazot.uz', 'Malika Rasulova', 'Iqtisodchi', '+998790101208', 'NAV-AZOT', 'ECONOMIST')
) AS v(id, username, email, full_name, position, phone, department_code, role_code)
JOIN departments d ON d.code = v.department_code AND d.is_deleted = false
JOIN roles r ON r.code = v.role_code AND r.is_deleted = false
ON CONFLICT (username) DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM (VALUES
    ('nav.azot.chief.mechanic', 'CHIEF_MECHANIC'),
    ('nav.azot.planner', 'PPR_ENGINEER'),
    ('nav.azot.mech.foreman', 'FOREMAN'),
    ('nav.azot.elec.foreman', 'FOREMAN'),
    ('nav.azot.transport.foreman', 'FOREMAN'),
    ('nav.azot.warehouse.head', 'STOREKEEPER'),
    ('nav.azot.storekeeper', 'STOREKEEPER'),
    ('nav.azot.economist', 'ECONOMIST')
) AS v(username, role_code)
JOIN users u ON u.username = v.username AND u.is_deleted = false
JOIN roles r ON r.code = v.role_code AND r.is_deleted = false
WHERE NOT EXISTS (
    SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role_id = r.id
);

INSERT INTO hr_employees (id, created_at, updated_at, is_deleted, personnel_number, first_name, last_name, middle_name, position, department_id, brigade_id, user_id, hire_date, terminated_date, grade, phone, email, is_active)
SELECT v.id, now(), now(), false, v.personnel_number, v.first_name, v.last_name, v.middle_name, v.position,
       d.id, NULL, u.id, v.hire_date, NULL, v.grade, v.phone, v.email, true
FROM (VALUES
    ('71000000-0000-0000-0000-000000000301'::uuid, 'NAV-AZOT-EMP-001', 'Bahodir', 'Karimov', 'Anvarovich', 'Zavod bosh mexanigi', 'NAV-AZOT-MEX', 'nav.azot.chief.mechanic', DATE '2017-02-13', 'M1', '+998790101201', 'chief.mechanic@navoiyazot.uz'),
    ('71000000-0000-0000-0000-000000000302'::uuid, 'NAV-AZOT-EMP-002', 'Dilshod', 'Tursunov', 'Mahmudovich', 'Ta''mirlash rejalashtiruvchisi', 'NAV-AZOT-MEX', 'nav.azot.planner', DATE '2019-04-08', 'M2', '+998790101202', 'planner@navoiyazot.uz'),
    ('71000000-0000-0000-0000-000000000303'::uuid, 'NAV-AZOT-EMP-003', 'Sardor', 'Yuldashev', 'Komilovich', 'Mexanik ta''mirlash brigadiri', 'NAV-AZOT-MEX', 'nav.azot.mech.foreman', DATE '2015-06-22', '6', '+998790101203', 'mechanical.foreman@navoiyazot.uz'),
    ('71000000-0000-0000-0000-000000000304'::uuid, 'NAV-AZOT-EMP-004', 'Jasur', 'Rakhimov', 'Rustamovich', 'Elektr-NQOA brigadiri', 'NAV-AZOT-ELEC', 'nav.azot.elec.foreman', DATE '2016-03-14', '6', '+998790101204', 'electrical.foreman@navoiyazot.uz'),
    ('71000000-0000-0000-0000-000000000305'::uuid, 'NAV-AZOT-EMP-005', 'Akmal', 'Saidov', 'Furqatovich', 'Transport ta''mirlash brigadiri', 'NAV-AZOT-TR', 'nav.azot.transport.foreman', DATE '2018-01-17', '5', '+998790101205', 'transport.foreman@navoiyazot.uz'),
    ('71000000-0000-0000-0000-000000000306'::uuid, 'NAV-AZOT-EMP-006', 'Gulnora', 'Ismoilova', 'Shavkatovna', 'Ombor mudiri', 'NAV-AZOT-WH', 'nav.azot.warehouse.head', DATE '2020-05-11', 'M2', '+998790101206', 'warehouse.head@navoiyazot.uz'),
    ('71000000-0000-0000-0000-000000000307'::uuid, 'NAV-AZOT-EMP-007', 'Nodira', 'Akramova', 'Bakhtiyorovna', 'Omborchi', 'NAV-AZOT-WH', 'nav.azot.storekeeper', DATE '2021-09-06', '4', '+998790101207', 'storekeeper@navoiyazot.uz'),
    ('71000000-0000-0000-0000-000000000308'::uuid, 'NAV-AZOT-EMP-008', 'Malika', 'Rasulova', 'Azizovna', 'Iqtisodchi', 'NAV-AZOT', 'nav.azot.economist', DATE '2018-11-19', 'M2', '+998790101208', 'economist@navoiyazot.uz'),
    ('71000000-0000-0000-0000-000000000309'::uuid, 'NAV-AZOT-EMP-009', 'Oybek', 'Usmonov', 'Ganiyevich', 'Mexanik muhandis', 'NAV-AZOT-AMM', NULL, DATE '2014-08-04', '5', '+998790101209', 'oybek.usmonov@navoiyazot.uz'),
    ('71000000-0000-0000-0000-000000000310'::uuid, 'NAV-AZOT-EMP-010', 'Bekzod', 'Nazarov', 'Ilhomovich', 'Chilangar', 'NAV-AZOT-MEX', NULL, DATE '2019-10-21', '5', '+998790101210', 'bekzod.nazarov@navoiyazot.uz'),
    ('71000000-0000-0000-0000-000000000311'::uuid, 'NAV-AZOT-EMP-011', 'Ulugbek', 'Hamroyev', 'Sobirovich', 'Chilangar', 'NAV-AZOT-MEX', NULL, DATE '2020-02-03', '4', '+998790101211', 'ulugbek.hamroyev@navoiyazot.uz'),
    ('71000000-0000-0000-0000-000000000312'::uuid, 'NAV-AZOT-EMP-012', 'Farrukh', 'Murodov', 'Alisherovich', 'Payvandchi', 'NAV-AZOT-MEX', NULL, DATE '2016-12-15', '5', '+998790101212', 'farrukh.murodov@navoiyazot.uz'),
    ('71000000-0000-0000-0000-000000000313'::uuid, 'NAV-AZOT-EMP-013', 'Shuhrat', 'Qodirov', 'Baxromovich', 'Elektr muhandisi', 'NAV-AZOT-ELEC', NULL, DATE '2015-09-28', '5', '+998790101213', 'shuhrat.qodirov@navoiyazot.uz'),
    ('71000000-0000-0000-0000-000000000314'::uuid, 'NAV-AZOT-EMP-014', 'Aziz', 'Kurbanov', 'Mirzoevich', 'NQOA muhandisi', 'NAV-AZOT-ELEC', NULL, DATE '2017-07-10', '5', '+998790101214', 'aziz.kurbanov@navoiyazot.uz'),
    ('71000000-0000-0000-0000-000000000315'::uuid, 'NAV-AZOT-EMP-015', 'Sherzod', 'Abdullayev', 'Ibrohimovich', 'Avtomexanik', 'NAV-AZOT-TR', NULL, DATE '2020-06-23', '4', '+998790101215', 'sherzod.abdullayev@navoiyazot.uz'),
    ('71000000-0000-0000-0000-000000000316'::uuid, 'NAV-AZOT-EMP-016', 'Anvar', 'Norboyev', 'Salimovich', 'Haydovchi', 'NAV-AZOT-TR', NULL, DATE '2014-01-20', '4', '+998790101216', 'anvar.norboyev@navoiyazot.uz'),
    ('71000000-0000-0000-0000-000000000317'::uuid, 'NAV-AZOT-EMP-017', 'Jamshid', 'Ergashev', 'Olimovich', 'Haydovchi', 'NAV-AZOT-TR', NULL, DATE '2016-04-18', '4', '+998790101217', 'jamshid.ergashev@navoiyazot.uz'),
    ('71000000-0000-0000-0000-000000000318'::uuid, 'NAV-AZOT-EMP-018', 'Murod', 'Aliyev', 'Hamidovich', 'Haydovchi', 'NAV-AZOT-TR', NULL, DATE '2019-08-26', '4', '+998790101218', 'murod.aliyev@navoiyazot.uz'),
    ('71000000-0000-0000-0000-000000000319'::uuid, 'NAV-AZOT-EMP-019', 'Sanjar', 'Mirzaev', 'Tohirjonovich', 'Nasos agregatlari ustasi', 'NAV-AZOT-AMM', NULL, DATE '2018-05-07', '5', '+998790101219', 'sanjar.mirzaev@navoiyazot.uz'),
    ('71000000-0000-0000-0000-000000000320'::uuid, 'NAV-AZOT-EMP-020', 'Ravshan', 'Sattorov', 'Orifovich', 'Reaktor apparatchisi', 'NAV-AZOT-KARB', NULL, DATE '2013-11-11', '5', '+998790101220', 'ravshan.sattorov@navoiyazot.uz'),
    ('71000000-0000-0000-0000-000000000321'::uuid, 'NAV-AZOT-EMP-021', 'Laziz', 'Xolmatov', 'Davronovich', 'Texnologik liniya ustasi', 'NAV-AZOT-AK', NULL, DATE '2017-02-27', '5', '+998790101221', 'laziz.xolmatov@navoiyazot.uz'),
    ('71000000-0000-0000-0000-000000000322'::uuid, 'NAV-AZOT-EMP-022', 'Diyor', 'Sobirov', 'Nematovich', 'Ombor hisobchisi', 'NAV-AZOT-WH', NULL, DATE '2022-01-24', '4', '+998790101222', 'diyor.sobirov@navoiyazot.uz')
) AS v(id, personnel_number, first_name, last_name, middle_name, position, department_code, username, hire_date, grade, phone, email)
JOIN departments d ON d.code = v.department_code AND d.is_deleted = false
LEFT JOIN users u ON u.username = v.username AND u.is_deleted = false
ON CONFLICT (personnel_number) DO NOTHING;

INSERT INTO brigades (id, created_at, updated_at, is_deleted, code, name, department_id, foreman_id, is_active, specialization)
SELECT v.id, now(), now(), false, v.code, v.name, d.id, u.id, true, v.specialization
FROM (VALUES
    ('71000000-0000-0000-0000-000000000401'::uuid, 'NAV-AZOT-BR-MEX', 'Mexanik ta''mirlash brigadasi', 'NAV-AZOT-MEX', 'nav.azot.mech.foreman', 'Nasos, kompressor, reduktor va quvur armaturalarini ta''mirlash'),
    ('71000000-0000-0000-0000-000000000402'::uuid, 'NAV-AZOT-BR-ELEC', 'Elektr-NQOA brigadasi', 'NAV-AZOT-ELEC', 'nav.azot.elec.foreman', 'Elektr dvigatellar, himoya apparatlari va NQOA konturlarini sozlash'),
    ('71000000-0000-0000-0000-000000000403'::uuid, 'NAV-AZOT-BR-TR', 'Transport ta''mirlash brigadasi', 'NAV-AZOT-TR', 'nav.azot.transport.foreman', 'Avtotransport, yuk texnikasi va maxsus mexanizmlar ta''miri')
) AS v(id, code, name, department_code, foreman_username, specialization)
JOIN departments d ON d.code = v.department_code AND d.is_deleted = false
JOIN users u ON u.username = v.foreman_username AND u.is_deleted = false
ON CONFLICT (code) DO NOTHING;

INSERT INTO brigade_members (id, created_at, updated_at, is_deleted, brigade_id, user_id, role_code, grade, qualifications, is_active)
SELECT v.id, now(), now(), false, b.id, u.id, v.role_code, v.grade, v.qualifications::jsonb, true
FROM (VALUES
    ('71000000-0000-0000-0000-000000000421'::uuid, 'NAV-AZOT-BR-MEX', 'nav.azot.mech.foreman', 'FOREMAN', 6, '["ROTATING_EQUIPMENT","HOT_WORK","LOTO"]'),
    ('71000000-0000-0000-0000-000000000422'::uuid, 'NAV-AZOT-BR-ELEC', 'nav.azot.elec.foreman', 'FOREMAN', 6, '["ELECTRICAL_SAFETY","INSTRUMENTATION","LOTO"]'),
    ('71000000-0000-0000-0000-000000000423'::uuid, 'NAV-AZOT-BR-TR', 'nav.azot.transport.foreman', 'FOREMAN', 5, '["FLEET_REPAIR","HYDRAULICS","ROAD_SAFETY"]')
) AS v(id, brigade_code, username, role_code, grade, qualifications)
JOIN brigades b ON b.code = v.brigade_code AND b.is_deleted = false
JOIN users u ON u.username = v.username AND u.is_deleted = false
ON CONFLICT (brigade_id, user_id) DO NOTHING;

UPDATE hr_employees e
SET brigade_id = b.id,
    updated_at = now()
FROM brigades b
WHERE e.is_deleted = false
  AND e.brigade_id IS NULL
  AND (
      (b.code = 'NAV-AZOT-BR-MEX' AND e.personnel_number IN ('NAV-AZOT-EMP-003','NAV-AZOT-EMP-010','NAV-AZOT-EMP-011','NAV-AZOT-EMP-012','NAV-AZOT-EMP-019'))
      OR (b.code = 'NAV-AZOT-BR-ELEC' AND e.personnel_number IN ('NAV-AZOT-EMP-004','NAV-AZOT-EMP-013','NAV-AZOT-EMP-014'))
      OR (b.code = 'NAV-AZOT-BR-TR' AND e.personnel_number IN ('NAV-AZOT-EMP-005','NAV-AZOT-EMP-015','NAV-AZOT-EMP-016','NAV-AZOT-EMP-017','NAV-AZOT-EMP-018'))
  );
