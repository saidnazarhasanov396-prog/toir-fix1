INSERT INTO departments (id, created_at, updated_at, is_deleted, code, name, name_en, name_uz, type, parent_id, description)
VALUES
('71000000-0000-0000-0000-000000000001', now(), now(), false, 'NAV-AZOT', 'Navoiy Azot', 'Navoiy Azot', 'Navoiy Azot', 'ENTERPRISE', NULL, 'Kimyo sanoati korxonasi uchun TOIR boshqaruv doirasi'),
('71000000-0000-0000-0000-000000000002', now(), now(), false, 'NAV-AZOT-AMM', 'Ammiak ishlab chiqarish sexi', 'Ammonia production workshop', 'Ammiak ishlab chiqarish sexi', 'WORKSHOP', '71000000-0000-0000-0000-000000000001', 'Ammiak ishlab chiqarish qurilmalari va yordamchi tizimlari'),
('71000000-0000-0000-0000-000000000003', now(), now(), false, 'NAV-AZOT-KARB', 'Karbamid ishlab chiqarish sexi', 'Urea production workshop', 'Karbamid ishlab chiqarish sexi', 'WORKSHOP', '71000000-0000-0000-0000-000000000001', 'Karbamid sintez va granulyatsiya uchastkalari'),
('71000000-0000-0000-0000-000000000004', now(), now(), false, 'NAV-AZOT-AK', 'Azot kislotasi sexi', 'Nitric acid workshop', 'Azot kislotasi sexi', 'WORKSHOP', '71000000-0000-0000-0000-000000000001', 'Azot kislotasi texnologik liniyalari'),
('71000000-0000-0000-0000-000000000005', now(), now(), false, 'NAV-AZOT-MEX', 'Mexanik ta''mirlash xizmati', 'Mechanical maintenance service', 'Mexanik ta''mirlash xizmati', 'SERVICE', '71000000-0000-0000-0000-000000000001', 'Aylanma va statik uskunalarni ta''mirlash xizmati'),
('71000000-0000-0000-0000-000000000006', now(), now(), false, 'NAV-AZOT-ELEC', 'Elektr va nazorat-o''lchov asboblari xizmati', 'Electrical and instrumentation service', 'Elektr va nazorat-o''lchov asboblari xizmati', 'SERVICE', '71000000-0000-0000-0000-000000000001', 'Elektr jihozlari, avtomatika va NQOA tizimlari xizmati'),
('71000000-0000-0000-0000-000000000007', now(), now(), false, 'NAV-AZOT-TR', 'Transport xizmati', 'Transport service', 'Transport xizmati', 'SERVICE', '71000000-0000-0000-0000-000000000001', 'Korxona transporti va maxsus texnika xizmati'),
('71000000-0000-0000-0000-000000000008', now(), now(), false, 'NAV-AZOT-WH', 'Markaziy ombor xo''jaligi', 'Central warehouse department', 'Markaziy ombor xo''jaligi', 'SERVICE', '71000000-0000-0000-0000-000000000001', 'Ehtiyot qismlar, materiallar va sarf ombori')
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    name_en = EXCLUDED.name_en,
    name_uz = EXCLUDED.name_uz,
    type = EXCLUDED.type,
    parent_id = EXCLUDED.parent_id,
    description = EXCLUDED.description,
    is_deleted = false,
    updated_at = now();

INSERT INTO locations (id, created_at, updated_at, is_deleted, code, name, name_en, name_uz, type, parent_id, department_id, description)
VALUES
('71000000-0000-0000-0000-000000000101', now(), now(), false, 'NAV-LOC-MAIN', 'Navoiy Azot asosiy ishlab chiqarish maydoni', 'Navoiy Azot main production site', 'Navoiy Azot asosiy ishlab chiqarish maydoni', 'SITE', NULL, '71000000-0000-0000-0000-000000000001', 'Asosiy texnologik hudud'),
('71000000-0000-0000-0000-000000000102', now(), now(), false, 'NAV-LOC-AMM-COMP', 'Ammiak sexi kompressor zali', 'Ammonia compressor hall', 'Ammiak sexi kompressor zali', 'BUILDING', '71000000-0000-0000-0000-000000000101', '71000000-0000-0000-0000-000000000002', 'Sintez gaz kompressorlari va moy tizimlari'),
('71000000-0000-0000-0000-000000000103', now(), now(), false, 'NAV-LOC-AMM-PUMP', 'Ammiak sexi nasos agregatlari uchastkasi', 'Ammonia pump unit section', 'Ammiak sexi nasos agregatlari uchastkasi', 'SECTION', '71000000-0000-0000-0000-000000000101', '71000000-0000-0000-0000-000000000002', 'Kondensat va aylanma nasoslar uchastkasi'),
('71000000-0000-0000-0000-000000000104', now(), now(), false, 'NAV-LOC-KARB-REACTOR', 'Karbamid reaktor bloki', 'Urea reactor block', 'Karbamid reaktor bloki', 'ZONE', '71000000-0000-0000-0000-000000000101', '71000000-0000-0000-0000-000000000003', 'Yuqori bosimli reaktor va yordamchi apparatlar'),
('71000000-0000-0000-0000-000000000105', now(), now(), false, 'NAV-LOC-KARB-GRAN', 'Karbamid granulyatsiya liniyasi', 'Urea granulation line', 'Karbamid granulyatsiya liniyasi', 'LINE', '71000000-0000-0000-0000-000000000101', '71000000-0000-0000-0000-000000000003', 'Granulyatsiya, sovitish va qadoqlash liniyasi'),
('71000000-0000-0000-0000-000000000106', now(), now(), false, 'NAV-LOC-AK-LINE', 'Azot kislotasi texnologik liniyasi', 'Nitric acid process line', 'Azot kislotasi texnologik liniyasi', 'LINE', '71000000-0000-0000-0000-000000000101', '71000000-0000-0000-0000-000000000004', 'Oksidlash va absorbsiya liniyasi'),
('71000000-0000-0000-0000-000000000107', now(), now(), false, 'NAV-LOC-MECH', 'Markaziy ta''mirlash ustaxonasi', 'Central mechanical workshop', 'Markaziy ta''mirlash ustaxonasi', 'WORKSHOP', '71000000-0000-0000-0000-000000000101', '71000000-0000-0000-0000-000000000005', 'Mexanik ishlov, yig''ish va payvandlash postlari'),
('71000000-0000-0000-0000-000000000108', now(), now(), false, 'NAV-LOC-TRANSPORT', 'Transport parki', 'Transport yard', 'Transport parki', 'PLATFORM', '71000000-0000-0000-0000-000000000101', '71000000-0000-0000-0000-000000000007', 'Yengil, yuk va maxsus texnika turargohi'),
('71000000-0000-0000-0000-000000000109', now(), now(), false, 'NAV-LOC-WH', 'Markaziy ehtiyot qismlar ombori', 'Central spare parts warehouse', 'Markaziy ehtiyot qismlar ombori', 'WAREHOUSE', '71000000-0000-0000-0000-000000000101', '71000000-0000-0000-0000-000000000008', 'Ehtiyot qismlar va sarf materiallari saqlash ombori'),
('71000000-0000-0000-0000-000000000110', now(), now(), false, 'NAV-LOC-ELEC', 'Energetika va NQOA xonasi', 'Electrical and instrumentation room', 'Energetika va NQOA xonasi', 'ROOM', '71000000-0000-0000-0000-000000000101', '71000000-0000-0000-0000-000000000006', 'Elektr himoya, avtomatika va kalibrlash jihozlari xonasi')
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    name_en = EXCLUDED.name_en,
    name_uz = EXCLUDED.name_uz,
    type = EXCLUDED.type,
    parent_id = EXCLUDED.parent_id,
    department_id = EXCLUDED.department_id,
    description = EXCLUDED.description,
    is_deleted = false,
    updated_at = now();
