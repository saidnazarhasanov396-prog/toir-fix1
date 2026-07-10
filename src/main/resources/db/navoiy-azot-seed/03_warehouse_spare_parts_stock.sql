INSERT INTO spare_part_types (id, code, name, description, default_unit, active, created_at, updated_at)
VALUES
('71000000-0000-0000-0000-000000000501', 'NAV-BEARING', 'Podshipniklar', 'Aylanma uskunalar uchun podshipniklar va uzellar', 'PCS', true, now(), now()),
('71000000-0000-0000-0000-000000000502', 'NAV-SEAL', 'Salnik va zichlagichlar', 'Nasos, kompressor va armatura zichlagichlari', 'PCS', true, now(), now()),
('71000000-0000-0000-0000-000000000503', 'NAV-GASKET', 'Prokladkalar', 'Flanes va apparat birikmalari uchun prokladkalar', 'PCS', true, now(), now()),
('71000000-0000-0000-0000-000000000504', 'NAV-BELT', 'Kamarlar', 'Ventilyator va yordamchi yuritmalar uchun V-kamarlar', 'PCS', true, now(), now()),
('71000000-0000-0000-0000-000000000505', 'NAV-FILTER', 'Filtrlar', 'Moy, havo va yoqilgi filtr elementlari', 'PCS', true, now(), now()),
('71000000-0000-0000-0000-000000000506', 'NAV-HYDRAULIC', 'Gidravlik qismlar', 'Gidravlik shlang va ulanish qismlari', 'PCS', true, now(), now()),
('71000000-0000-0000-0000-000000000507', 'NAV-ELECTRICAL', 'Elektr qismlar', 'Elektr himoya va boshqaruv apparatlari', 'PCS', true, now(), now()),
('71000000-0000-0000-0000-000000000508', 'NAV-INSTRUMENT', 'NQOA qismlari', 'Bosim, harorat va o''lchov asboblari', 'PCS', true, now(), now()),
('71000000-0000-0000-0000-000000000509', 'NAV-OIL', 'Moylar', 'Reduktor, kompressor va umumiy moylash materiallari', 'L', true, now(), now()),
('71000000-0000-0000-0000-000000000510', 'NAV-CONSUMABLE', 'Sarf materiallari', 'Payvandlash, mahkamlash va umumiy sarf materiallari', 'PCS', true, now(), now()),
('71000000-0000-0000-0000-000000000511', 'NAV-PUMP-KIT', 'Nasos remkomplektlari', 'Nasoslar uchun ta''mirlash komplektlari', 'SET', true, now(), now()),
('71000000-0000-0000-0000-000000000512', 'NAV-VEHICLE-PART', 'Transport ehtiyot qismlari', 'Avtotransport va maxsus texnika ehtiyot qismlari', 'PCS', true, now(), now())
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    description = EXCLUDED.description,
    default_unit = EXCLUDED.default_unit,
    active = EXCLUDED.active,
    updated_at = now();

INSERT INTO warehouses (id, created_at, updated_at, is_deleted, code, name, department_id, location_id, responsible_id, is_active)
SELECT '71000000-0000-0000-0000-000000000601'::uuid, now(), now(), false,
       'NAV-WH-MAIN', 'Navoiy Azot markaziy ehtiyot qismlar ombori',
       d.id, l.id, e.id, true
FROM departments d
JOIN locations l ON l.code = 'NAV-LOC-WH' AND l.is_deleted = false
JOIN hr_employees e ON e.personnel_number = 'NAV-AZOT-EMP-006' AND e.is_deleted = false
WHERE d.code = 'NAV-AZOT-WH'
  AND d.is_deleted = false
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    department_id = EXCLUDED.department_id,
    location_id = EXCLUDED.location_id,
    responsible_id = EXCLUDED.responsible_id,
    is_active = EXCLUDED.is_active,
    is_deleted = false,
    updated_at = now();

INSERT INTO spare_parts (
    id, created_at, updated_at, is_deleted,
    code, name, sku, kind, type_id, type, unit, specification, manufacturer,
    min_stock, lead_time_days, last_purchase_price, average_cost, last_purchase_cost,
    inventory_value, criticality
)
SELECT v.id, now(), now(), false,
       v.code, v.name, v.sku, v.kind, spt.id, v.legacy_type, v.unit, v.specification, v.manufacturer,
       v.min_stock, v.lead_time_days, v.unit_price, v.unit_price, v.unit_price,
       v.unit_price * v.opening_qty, v.criticality
FROM (VALUES
    ('71000000-0000-0000-0000-000000000701'::uuid, 'NAV-SP-BRG-6312', 'Podshipnik 6312', 'NAV-AZOT-SKU-0001', 'SPARE_PART', 'NAV-BEARING', 'BEARING', 'PCS', '6312 C3, yopiq turdagi', 'SKF', 12, 30, 420000::numeric, 34::numeric, 'HIGH'),
    ('71000000-0000-0000-0000-000000000702'::uuid, 'NAV-SP-BRG-6208', 'Podshipnik 6208', 'NAV-AZOT-SKU-0002', 'SPARE_PART', 'NAV-BEARING', 'BEARING', 'PCS', '6208 C3, nasos yuritmasi uchun', 'FAG', 20, 25, 190000::numeric, 58::numeric, 'MEDIUM'),
    ('71000000-0000-0000-0000-000000000703'::uuid, 'NAV-SP-SEAL-45', 'Mexanik salnik 45 mm', 'NAV-AZOT-SKU-0003', 'SPARE_PART', 'NAV-SEAL', 'MECHANICAL_PART', 'PCS', 'Karbid-grafit juftlik, 45 mm', 'Burgmann', 8, 35, 1250000::numeric, 18::numeric, 'CRITICAL'),
    ('71000000-0000-0000-0000-000000000704'::uuid, 'NAV-SP-SEAL-60', 'Mexanik salnik 60 mm', 'NAV-AZOT-SKU-0004', 'SPARE_PART', 'NAV-SEAL', 'MECHANICAL_PART', 'PCS', 'Karbid-grafit juftlik, 60 mm', 'Burgmann', 6, 40, 1680000::numeric, 14::numeric, 'CRITICAL'),
    ('71000000-0000-0000-0000-000000000705'::uuid, 'NAV-SP-GASKET-DN100', 'Flanes prokladka DN100', 'NAV-AZOT-SKU-0005', 'CONSUMABLE', 'NAV-GASKET', 'CONSUMABLE', 'PCS', 'Paronit PN16 DN100', 'UzSeal', 40, 12, 32000::numeric, 76::numeric, 'MEDIUM'),
    ('71000000-0000-0000-0000-000000000706'::uuid, 'NAV-SP-GASKET-DN150', 'Flanes prokladka DN150', 'NAV-AZOT-SKU-0006', 'CONSUMABLE', 'NAV-GASKET', 'CONSUMABLE', 'PCS', 'Paronit PN16 DN150', 'UzSeal', 30, 12, 46000::numeric, 64::numeric, 'MEDIUM'),
    ('71000000-0000-0000-0000-000000000707'::uuid, 'NAV-SP-BELT-B1800', 'V-kamar B-1800', 'NAV-AZOT-SKU-0007', 'CONSUMABLE', 'NAV-BELT', 'BELT', 'PCS', 'B profilli yuritma kamari, 1800 mm', 'Optibelt', 10, 18, 145000::numeric, 28::numeric, 'MEDIUM'),
    ('71000000-0000-0000-0000-000000000708'::uuid, 'NAV-SP-BELT-C2240', 'V-kamar C-2240', 'NAV-AZOT-SKU-0008', 'CONSUMABLE', 'NAV-BELT', 'BELT', 'PCS', 'C profilli yuritma kamari, 2240 mm', 'Optibelt', 8, 18, 230000::numeric, 22::numeric, 'MEDIUM'),
    ('71000000-0000-0000-0000-000000000709'::uuid, 'NAV-SP-OIL-FILTER', 'Moy filtri', 'NAV-AZOT-SKU-0009', 'SPARE_PART', 'NAV-FILTER', 'FILTER', 'PCS', 'Kompressor moy tizimi filtri', 'Mann Filter', 15, 20, 175000::numeric, 36::numeric, 'HIGH'),
    ('71000000-0000-0000-0000-000000000710'::uuid, 'NAV-SP-AIR-FILTER', 'Havo filtri', 'NAV-AZOT-SKU-0010', 'SPARE_PART', 'NAV-FILTER', 'FILTER', 'PCS', 'Sanoat ventilyatsiya havo filtri', 'Donaldson', 15, 20, 210000::numeric, 32::numeric, 'MEDIUM'),
    ('71000000-0000-0000-0000-000000000711'::uuid, 'NAV-SP-HOSE-12', 'Gidravlik shlang 1/2', 'NAV-AZOT-SKU-0011', 'MATERIAL', 'NAV-HYDRAULIC', 'MECHANICAL_PART', 'M', 'Ikki qatlamli gidravlik shlang 1/2 dyuym', 'Parker', 20, 15, 78000::numeric, 120::numeric, 'MEDIUM'),
    ('71000000-0000-0000-0000-000000000712'::uuid, 'NAV-SP-HOSE-34', 'Gidravlik shlang 3/4', 'NAV-AZOT-SKU-0012', 'MATERIAL', 'NAV-HYDRAULIC', 'MECHANICAL_PART', 'M', 'Ikki qatlamli gidravlik shlang 3/4 dyuym', 'Parker', 16, 15, 115000::numeric, 90::numeric, 'MEDIUM'),
    ('71000000-0000-0000-0000-000000000713'::uuid, 'NAV-SP-CONTACTOR-220', 'Kontaktor 220V', 'NAV-AZOT-SKU-0013', 'SPARE_PART', 'NAV-ELECTRICAL', 'ELECTRICAL_PART', 'PCS', '220V boshqaruv g''altakli kontaktor', 'Schneider Electric', 6, 28, 680000::numeric, 16::numeric, 'HIGH'),
    ('71000000-0000-0000-0000-000000000714'::uuid, 'NAV-SP-BREAKER-63A', 'Avtomat himoya 63A', 'NAV-AZOT-SKU-0014', 'SPARE_PART', 'NAV-ELECTRICAL', 'ELECTRICAL_PART', 'PCS', '3 fazali 63A himoya avtomati', 'ABB', 6, 28, 520000::numeric, 18::numeric, 'HIGH'),
    ('71000000-0000-0000-0000-000000000715'::uuid, 'NAV-SP-GAUGE-16BAR', 'Manometr 0-16 bar', 'NAV-AZOT-SKU-0015', 'SPARE_PART', 'NAV-INSTRUMENT', 'OTHER', 'PCS', 'Texnologik liniya uchun 0-16 bar manometr', 'Wika', 8, 21, 360000::numeric, 20::numeric, 'HIGH'),
    ('71000000-0000-0000-0000-000000000716'::uuid, 'NAV-SP-PT100', 'Harorat datchigi PT100', 'NAV-AZOT-SKU-0016', 'SPARE_PART', 'NAV-INSTRUMENT', 'OTHER', 'PCS', 'PT100, zanglamas gilza bilan', 'Endress Hauser', 6, 30, 890000::numeric, 12::numeric, 'HIGH'),
    ('71000000-0000-0000-0000-000000000717'::uuid, 'NAV-SP-OIL-VG220', 'Reduktor moyi ISO VG 220', 'NAV-AZOT-SKU-0017', 'MATERIAL', 'NAV-OIL', 'OIL', 'L', 'Sanoat reduktorlari uchun ISO VG 220', 'Shell', 80, 20, 52000::numeric, 260::numeric, 'MEDIUM'),
    ('71000000-0000-0000-0000-000000000718'::uuid, 'NAV-SP-OIL-VG46', 'Kompressor moyi ISO VG 46', 'NAV-AZOT-SKU-0018', 'MATERIAL', 'NAV-OIL', 'OIL', 'L', 'Kompressor moy tizimi uchun ISO VG 46', 'Mobil', 100, 20, 61000::numeric, 240::numeric, 'HIGH'),
    ('71000000-0000-0000-0000-000000000719'::uuid, 'NAV-SP-ELECTRODE-E42', 'Elektrod E42', 'NAV-AZOT-SKU-0019', 'CONSUMABLE', 'NAV-CONSUMABLE', 'CONSUMABLE', 'KG', 'Payvandlash elektrodi E42', 'TashWeld', 50, 10, 28500::numeric, 180::numeric, 'LOW'),
    ('71000000-0000-0000-0000-000000000720'::uuid, 'NAV-SP-BOLT-M16', 'Bolt-gayka M16 komplekt', 'NAV-AZOT-SKU-0020', 'CONSUMABLE', 'NAV-CONSUMABLE', 'FASTENER', 'SET', 'M16x70 bolt, gayka va shayba komplekti', 'UzFastener', 120, 10, 8200::numeric, 320::numeric, 'LOW'),
    ('71000000-0000-0000-0000-000000000721'::uuid, 'NAV-SP-PUMP-KIT-NK200', 'Nasos remkomplekti NK-200', 'NAV-AZOT-SKU-0021', 'SPARE_PART', 'NAV-PUMP-KIT', 'MECHANICAL_PART', 'SET', 'NK-200 nasosi uchun ishchi g''ildirak, salnik va podshipnik komplekti', 'UzPump', 3, 45, 4850000::numeric, 7::numeric, 'CRITICAL'),
    ('71000000-0000-0000-0000-000000000722'::uuid, 'NAV-SP-FAN-BRG-UNIT', 'Ventilyator podshipnik uzeli', 'NAV-AZOT-SKU-0022', 'SPARE_PART', 'NAV-BEARING', 'BEARING', 'PCS', 'Sanoat ventilyatori uchun podshipnik uzeli', 'SKF', 4, 35, 1420000::numeric, 9::numeric, 'HIGH'),
    ('71000000-0000-0000-0000-000000000723'::uuid, 'NAV-SP-BATTERY-100AH', 'Akkumulyator 12V 100Ah', 'NAV-AZOT-SKU-0023', 'SPARE_PART', 'NAV-VEHICLE-PART', 'OTHER', 'PCS', 'Transport vositalari uchun 12V 100Ah akkumulyator', 'Mutlu', 4, 18, 1350000::numeric, 11::numeric, 'MEDIUM'),
    ('71000000-0000-0000-0000-000000000724'::uuid, 'NAV-SP-VEH-OIL-FILTER', 'Avtomobil moy filtri', 'NAV-AZOT-SKU-0024', 'SPARE_PART', 'NAV-VEHICLE-PART', 'FILTER', 'PCS', 'Yuk va xizmat avtomobillari uchun moy filtri', 'Mann Filter', 8, 14, 95000::numeric, 24::numeric, 'MEDIUM'),
    ('71000000-0000-0000-0000-000000000725'::uuid, 'NAV-SP-DIESEL-FILTER', 'Dizel yoqilgi filtri', 'NAV-AZOT-SKU-0025', 'SPARE_PART', 'NAV-VEHICLE-PART', 'FILTER', 'PCS', 'Dizel texnika uchun yoqilgi filtri', 'Fleetguard', 8, 14, 125000::numeric, 22::numeric, 'MEDIUM'),
    ('71000000-0000-0000-0000-000000000726'::uuid, 'NAV-SP-BRAKE-PAD', 'Tormoz kolodka komplekti', 'NAV-AZOT-SKU-0026', 'SPARE_PART', 'NAV-VEHICLE-PART', 'MECHANICAL_PART', 'SET', 'Yuk avtomobili old o''q kolodkalari', 'Ferodo', 4, 20, 740000::numeric, 10::numeric, 'HIGH'),
    ('71000000-0000-0000-0000-000000000727'::uuid, 'NAV-SP-VALVE-DN80', 'Zadvijka DN80 zichlagich komplekti', 'NAV-AZOT-SKU-0027', 'SPARE_PART', 'NAV-SEAL', 'MECHANICAL_PART', 'SET', 'DN80 armatura uchun zichlagich va shpindel salnigi', 'UzValve', 5, 25, 620000::numeric, 15::numeric, 'HIGH'),
    ('71000000-0000-0000-0000-000000000728'::uuid, 'NAV-SP-CABLE-KG25', 'Kuch kabeli KG 4x25', 'NAV-AZOT-SKU-0028', 'MATERIAL', 'NAV-ELECTRICAL', 'CABLE', 'M', 'Moslashuvchan kuch kabeli KG 4x25', 'Uzkabel', 30, 20, 98000::numeric, 150::numeric, 'MEDIUM'),
    ('71000000-0000-0000-0000-000000000729'::uuid, 'NAV-SP-GREASE-EP2', 'Surtma EP2', 'NAV-AZOT-SKU-0029', 'MATERIAL', 'NAV-OIL', 'GREASE', 'KG', 'Podshipniklar uchun litiyli EP2 surtma', 'Lukoil', 30, 12, 46000::numeric, 120::numeric, 'MEDIUM'),
    ('71000000-0000-0000-0000-000000000730'::uuid, 'NAV-SP-RESPIRATOR-A1', 'Respirator filtri A1', 'NAV-AZOT-SKU-0030', 'CONSUMABLE', 'NAV-CONSUMABLE', 'CONSUMABLE', 'PCS', 'Kimyoviy hudud ishlari uchun A1 turdagi respirator filtri', '3M', 40, 15, 68000::numeric, 90::numeric, 'LOW')
) AS v(id, code, name, sku, kind, type_code, legacy_type, unit, specification, manufacturer, min_stock, lead_time_days, unit_price, opening_qty, criticality)
JOIN spare_part_types spt ON spt.code = v.type_code AND spt.active = true
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    sku = EXCLUDED.sku,
    kind = EXCLUDED.kind,
    type_id = EXCLUDED.type_id,
    type = EXCLUDED.type,
    unit = EXCLUDED.unit,
    specification = EXCLUDED.specification,
    manufacturer = EXCLUDED.manufacturer,
    min_stock = EXCLUDED.min_stock,
    lead_time_days = EXCLUDED.lead_time_days,
    last_purchase_price = EXCLUDED.last_purchase_price,
    average_cost = EXCLUDED.average_cost,
    last_purchase_cost = EXCLUDED.last_purchase_cost,
    inventory_value = EXCLUDED.inventory_value,
    criticality = EXCLUDED.criticality,
    is_deleted = false,
    updated_at = now();

WITH opening_stock AS (
    SELECT sp.id AS spare_part_id,
           sp.code,
           v.opening_qty,
           v.reserved_qty,
           v.unit_cost
    FROM (VALUES
        ('NAV-SP-BRG-6312', 34::numeric, 2::numeric, 420000::numeric),
        ('NAV-SP-BRG-6208', 58::numeric, 4::numeric, 190000::numeric),
        ('NAV-SP-SEAL-45', 18::numeric, 1::numeric, 1250000::numeric),
        ('NAV-SP-SEAL-60', 14::numeric, 0::numeric, 1680000::numeric),
        ('NAV-SP-GASKET-DN100', 76::numeric, 5::numeric, 32000::numeric),
        ('NAV-SP-GASKET-DN150', 64::numeric, 4::numeric, 46000::numeric),
        ('NAV-SP-BELT-B1800', 28::numeric, 2::numeric, 145000::numeric),
        ('NAV-SP-BELT-C2240', 22::numeric, 1::numeric, 230000::numeric),
        ('NAV-SP-OIL-FILTER', 36::numeric, 2::numeric, 175000::numeric),
        ('NAV-SP-AIR-FILTER', 32::numeric, 2::numeric, 210000::numeric),
        ('NAV-SP-HOSE-12', 120::numeric, 10::numeric, 78000::numeric),
        ('NAV-SP-HOSE-34', 90::numeric, 6::numeric, 115000::numeric),
        ('NAV-SP-CONTACTOR-220', 16::numeric, 1::numeric, 680000::numeric),
        ('NAV-SP-BREAKER-63A', 18::numeric, 1::numeric, 520000::numeric),
        ('NAV-SP-GAUGE-16BAR', 20::numeric, 1::numeric, 360000::numeric),
        ('NAV-SP-PT100', 12::numeric, 1::numeric, 890000::numeric),
        ('NAV-SP-OIL-VG220', 260::numeric, 20::numeric, 52000::numeric),
        ('NAV-SP-OIL-VG46', 240::numeric, 15::numeric, 61000::numeric),
        ('NAV-SP-ELECTRODE-E42', 180::numeric, 12::numeric, 28500::numeric),
        ('NAV-SP-BOLT-M16', 320::numeric, 20::numeric, 8200::numeric),
        ('NAV-SP-PUMP-KIT-NK200', 7::numeric, 0::numeric, 4850000::numeric),
        ('NAV-SP-FAN-BRG-UNIT', 9::numeric, 0::numeric, 1420000::numeric),
        ('NAV-SP-BATTERY-100AH', 11::numeric, 1::numeric, 1350000::numeric),
        ('NAV-SP-VEH-OIL-FILTER', 24::numeric, 2::numeric, 95000::numeric),
        ('NAV-SP-DIESEL-FILTER', 22::numeric, 2::numeric, 125000::numeric),
        ('NAV-SP-BRAKE-PAD', 10::numeric, 1::numeric, 740000::numeric),
        ('NAV-SP-VALVE-DN80', 15::numeric, 1::numeric, 620000::numeric),
        ('NAV-SP-CABLE-KG25', 150::numeric, 10::numeric, 98000::numeric),
        ('NAV-SP-GREASE-EP2', 120::numeric, 8::numeric, 46000::numeric),
        ('NAV-SP-RESPIRATOR-A1', 90::numeric, 5::numeric, 68000::numeric)
    ) AS v(code, opening_qty, reserved_qty, unit_cost)
    JOIN spare_parts sp ON sp.code = v.code AND sp.is_deleted = false
),
main_warehouse AS (
    SELECT id FROM warehouses WHERE code = 'NAV-WH-MAIN' AND is_deleted = false
)
INSERT INTO warehouse_stock_balances (
    id, created_at, updated_at, is_deleted,
    warehouse_id, spare_part_id, bin_id, lot_number, serial_number, identity_key,
    expiry_date, qty_on_hand, qty_reserved, avg_cost, version, stock_status
)
SELECT gen_random_uuid(), now(), now(), false,
       w.id, os.spare_part_id, NULL, NULL, NULL,
       w.id::text || '|' || os.spare_part_id::text || '|0||||AVAILABLE',
       NULL, os.opening_qty, os.reserved_qty, os.unit_cost, 0, 'AVAILABLE'
FROM opening_stock os
CROSS JOIN main_warehouse w
ON CONFLICT (identity_key) WHERE is_deleted = false DO UPDATE
SET qty_on_hand = EXCLUDED.qty_on_hand,
    qty_reserved = EXCLUDED.qty_reserved,
    avg_cost = EXCLUDED.avg_cost,
    stock_status = EXCLUDED.stock_status,
    updated_at = now();

WITH opening_stock AS (
    SELECT sp.id AS spare_part_id,
           sp.code,
           v.opening_qty,
           v.unit_cost
    FROM (VALUES
        ('NAV-SP-BRG-6312', 34::numeric, 420000::numeric),
        ('NAV-SP-BRG-6208', 58::numeric, 190000::numeric),
        ('NAV-SP-SEAL-45', 18::numeric, 1250000::numeric),
        ('NAV-SP-SEAL-60', 14::numeric, 1680000::numeric),
        ('NAV-SP-GASKET-DN100', 76::numeric, 32000::numeric),
        ('NAV-SP-GASKET-DN150', 64::numeric, 46000::numeric),
        ('NAV-SP-BELT-B1800', 28::numeric, 145000::numeric),
        ('NAV-SP-BELT-C2240', 22::numeric, 230000::numeric),
        ('NAV-SP-OIL-FILTER', 36::numeric, 175000::numeric),
        ('NAV-SP-AIR-FILTER', 32::numeric, 210000::numeric),
        ('NAV-SP-HOSE-12', 120::numeric, 78000::numeric),
        ('NAV-SP-HOSE-34', 90::numeric, 115000::numeric),
        ('NAV-SP-CONTACTOR-220', 16::numeric, 680000::numeric),
        ('NAV-SP-BREAKER-63A', 18::numeric, 520000::numeric),
        ('NAV-SP-GAUGE-16BAR', 20::numeric, 360000::numeric),
        ('NAV-SP-PT100', 12::numeric, 890000::numeric),
        ('NAV-SP-OIL-VG220', 260::numeric, 52000::numeric),
        ('NAV-SP-OIL-VG46', 240::numeric, 61000::numeric),
        ('NAV-SP-ELECTRODE-E42', 180::numeric, 28500::numeric),
        ('NAV-SP-BOLT-M16', 320::numeric, 8200::numeric),
        ('NAV-SP-PUMP-KIT-NK200', 7::numeric, 4850000::numeric),
        ('NAV-SP-FAN-BRG-UNIT', 9::numeric, 1420000::numeric),
        ('NAV-SP-BATTERY-100AH', 11::numeric, 1350000::numeric),
        ('NAV-SP-VEH-OIL-FILTER', 24::numeric, 95000::numeric),
        ('NAV-SP-DIESEL-FILTER', 22::numeric, 125000::numeric),
        ('NAV-SP-BRAKE-PAD', 10::numeric, 740000::numeric),
        ('NAV-SP-VALVE-DN80', 15::numeric, 620000::numeric),
        ('NAV-SP-CABLE-KG25', 150::numeric, 98000::numeric),
        ('NAV-SP-GREASE-EP2', 120::numeric, 46000::numeric),
        ('NAV-SP-RESPIRATOR-A1', 90::numeric, 68000::numeric)
    ) AS v(code, opening_qty, unit_cost)
    JOIN spare_parts sp ON sp.code = v.code AND sp.is_deleted = false
),
main_warehouse AS (
    SELECT id FROM warehouses WHERE code = 'NAV-WH-MAIN' AND is_deleted = false
)
INSERT INTO warehouse_stock_ledgers (
    id, created_at, updated_at, is_deleted,
    warehouse_id, spare_part_id, bin_id, lot_number, serial_number, expiry_date, stock_status,
    movement_type, quantity, unit_cost, total_cost,
    reference_type, reference_id, reference_doc_no, idempotency_key, posted_at, notes
)
SELECT gen_random_uuid(), now(), now(), false,
       w.id, os.spare_part_id, NULL, NULL, NULL, NULL, 'AVAILABLE',
       'RECEIPT', os.opening_qty, os.unit_cost, os.opening_qty * os.unit_cost,
       'NAVOIY_AZOT_OPENING_BALANCE', NULL, 'NAV-AZOT-OPENING-2026',
       'navoiy-azot-opening-stock:' || w.id::text || ':' || os.spare_part_id::text,
       now(), 'Boshlang''ich ombor qoldig''i'
FROM opening_stock os
CROSS JOIN main_warehouse w
ON CONFLICT (idempotency_key) WHERE idempotency_key IS NOT NULL AND is_deleted = false DO UPDATE
SET quantity = EXCLUDED.quantity,
    unit_cost = EXCLUDED.unit_cost,
    total_cost = EXCLUDED.total_cost,
    updated_at = now();
