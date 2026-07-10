WITH category_values AS (
    SELECT *
    FROM (VALUES
        (1, 'NAV-COST-SPARE-PARTS', 'Ehtiyot qismlar', 'Spare parts', 'Ehtiyot qismlar', 'Ta''mirlash ishlarida ishlatiladigan ehtiyot qismlar'),
        (2, 'NAV-COST-CONSUMABLES', 'Sarf materiallari', 'Consumables', 'Sarf materiallari', 'Moy, elektrod, filtr va boshqa sarf materiallari'),
        (3, 'NAV-COST-LABOR', 'Ta''mirlash mehnat xarajatlari', 'Repair labor costs', 'Ta''mirlash mehnat xarajatlari', 'Ichki ta''mirlash brigadalari mehnat xarajatlari'),
        (4, 'NAV-COST-VEHICLE-MAINTENANCE', 'Transport texnik xizmati', 'Vehicle service costs', 'Transport texnik xizmati', 'Transport vositalari ta''miri va texnik xizmat xarajatlari'),
        (5, 'NAV-COST-ELECTRICAL-INSTRUMENTATION', 'Elektr va NQOA xarajatlari', 'Electrical and instrument costs', 'Elektr va NQOA xarajatlari', 'Elektr jihozlari va nazorat-o''lchov asboblari bo''yicha xarajatlar'),
        (6, 'NAV-COST-CONTRACTOR-SERVICES', 'Pudratchi xizmatlari', 'Contractor services', 'Pudratchi xizmatlari', 'Tashqi xizmatlar va ixtisoslashtirilgan diagnostika xarajatlari')
    ) AS v(idx, code, name, name_en, name_uz, description)
)
INSERT INTO cost_categories (
    id, created_at, updated_at, is_deleted,
    code, name, name_en, name_uz, description
)
SELECT ('71000000-0000-0000-0000-00000014' || lpad(cv.idx::text, 4, '0'))::uuid,
       now(), now(), false,
       cv.code, cv.name, cv.name_en, cv.name_uz, cv.description
FROM category_values cv
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    name_en = EXCLUDED.name_en,
    name_uz = EXCLUDED.name_uz,
    description = EXCLUDED.description,
    is_deleted = false,
    updated_at = now();

INSERT INTO maintenance_budgets (
    id, created_at, updated_at, is_deleted,
    year, month, department_id, status,
    total_planned, total_actual, total_committed
)
SELECT '71000000-0000-0000-0000-000000150001'::uuid,
       now(), now(), false,
       2026, NULL::integer, d.id, 'APPROVED',
       695000000.0::double precision, 0.0::double precision, 0.0::double precision
FROM departments d
WHERE d.code = 'NAV-AZOT'
  AND d.is_deleted = false
ON CONFLICT (id) DO UPDATE
SET year = EXCLUDED.year,
    month = EXCLUDED.month,
    department_id = EXCLUDED.department_id,
    status = EXCLUDED.status,
    is_deleted = false,
    updated_at = now();

WITH line_values AS (
    SELECT *
    FROM (VALUES
        (1, 'NAV-COST-SPARE-PARTS', 180000000.0::double precision, 'Ehtiyot qismlar bo''yicha yillik limit'),
        (2, 'NAV-COST-CONSUMABLES', 75000000.0::double precision, 'Sarf materiallari bo''yicha yillik limit'),
        (3, 'NAV-COST-LABOR', 120000000.0::double precision, 'Ichki brigadalar mehnati bo''yicha yillik limit'),
        (4, 'NAV-COST-VEHICLE-MAINTENANCE', 95000000.0::double precision, 'Transport texnik xizmati bo''yicha yillik limit'),
        (5, 'NAV-COST-ELECTRICAL-INSTRUMENTATION', 85000000.0::double precision, 'Elektr va NQOA ishlari bo''yicha yillik limit'),
        (6, 'NAV-COST-CONTRACTOR-SERVICES', 140000000.0::double precision, 'Pudratchi xizmatlari bo''yicha yillik limit')
    ) AS v(idx, category_code, planned_amount, description)
)
INSERT INTO budget_lines (
    id, created_at, updated_at, is_deleted,
    budget_id, cost_category_id, description,
    planned_amount, actual_amount, committed_amount
)
SELECT ('71000000-0000-0000-0000-00000016' || lpad(lv.idx::text, 4, '0'))::uuid,
       now(), now(), false,
       '71000000-0000-0000-0000-000000150001'::uuid,
       cc.id, lv.description,
       lv.planned_amount, 0.0::double precision, 0.0::double precision
FROM line_values lv
JOIN cost_categories cc ON cc.code = lv.category_code AND cc.is_deleted = false
ON CONFLICT (id) DO UPDATE
SET cost_category_id = EXCLUDED.cost_category_id,
    description = EXCLUDED.description,
    planned_amount = EXCLUDED.planned_amount,
    is_deleted = false,
    updated_at = now();

WITH actor AS (
    SELECT
        (SELECT id FROM users WHERE username = 'nav.azot.economist' AND is_deleted = false) AS economist_id,
        (SELECT id FROM users WHERE username = 'nav.azot.chief.mechanic' AND is_deleted = false) AS chief_mechanic_id,
        (SELECT id FROM users WHERE username = 'nav.azot.planner' AND is_deleted = false) AS planner_id,
        (SELECT id FROM users WHERE username = 'nav.azot.storekeeper' AND is_deleted = false) AS storekeeper_id,
        (SELECT id FROM users WHERE username = 'nav.azot.warehouse.head' AND is_deleted = false) AS warehouse_head_id
),
actual_values AS (
    SELECT *
    FROM (VALUES
        (1, 'NAV-COST-SPARE-PARTS', 'NAV-WO-2026-0001', NULL, 'MATERIAL_ISSUE', 'APPROVED', 25000000.0::double precision, TIMESTAMP '2026-07-20 09:00:00', 'Podshipnik va moylash materiallari ombordan chiqarildi', 'Ombor hujjatlari bo''yicha xarajat tasdiqlandi', NULL, 'Material xarajati ehtiyot qismlar limitiga biriktirildi', 'economist'),
        (2, 'NAV-COST-SPARE-PARTS', 'NAV-WO-2026-0002', NULL, 'MATERIAL_ISSUE', 'APPROVED', 18000000.0::double precision, TIMESTAMP '2026-07-21 09:00:00', 'Kompressor salnik uzeli uchun ehtiyot qismlar berildi', 'Ish buyrug''i va ombor chiqimi mos keladi', NULL, 'Material xarajati ehtiyot qismlar limitiga biriktirildi', 'chief'),
        (3, 'NAV-COST-CONSUMABLES', 'NAV-WO-2026-0003', NULL, 'MATERIAL_ISSUE', 'APPROVED', 8000000.0::double precision, TIMESTAMP '2026-07-22 09:00:00', 'Flanes tiklash uchun prokladka va yordamchi materiallar berildi', 'Material sarfi ish hajmiga mos', NULL, 'Sarf materiallari limitiga biriktirildi', 'economist'),
        (4, 'NAV-COST-CONSUMABLES', 'NAV-WO-2026-0004', NULL, 'MATERIAL_ISSUE', 'APPROVED', 6000000.0::double precision, TIMESTAMP '2026-07-23 09:00:00', 'Nazorat asbobi almashtirish uchun sarf materiallari berildi', 'Hujjatlar bo''yicha xarajat tasdiqlandi', NULL, 'Sarf materiallari limitiga biriktirildi', 'economist'),
        (5, 'NAV-COST-LABOR', 'NAV-WO-2026-0003', NULL, 'WORK_ORDER', 'APPROVED', 22000000.0::double precision, TIMESTAMP '2026-07-24 10:00:00', 'Flanes zichligini tiklash bo''yicha brigada mehnati hisoblandi', 'Bajarilgan ish dalolatnomasi asosida tasdiqlandi', NULL, 'Mehnat xarajati ichki brigada limitiga biriktirildi', 'chief'),
        (6, 'NAV-COST-LABOR', 'NAV-WO-2026-0006', NULL, 'WORK_ORDER', 'APPROVED', 18000000.0::double precision, TIMESTAMP '2026-07-25 10:00:00', 'Elektr dvigatel podshipnik qismini tiklash mehnat xarajati', 'Ish hajmi va mehnat soati kelishildi', NULL, 'Mehnat xarajati ichki brigada limitiga biriktirildi', 'chief'),
        (7, 'NAV-COST-VEHICLE-MAINTENANCE', 'NAV-WO-2026-0014', NULL, 'WORK_ORDER', 'APPROVED', 9000000.0::double precision, TIMESTAMP '2026-07-26 11:00:00', 'Avtoyuklagich filtrlarini almashtirish xarajati', 'Transport xizmati hujjatlari tasdiqlandi', NULL, 'Transport xizmati limitiga biriktirildi', 'economist'),
        (8, 'NAV-COST-VEHICLE-MAINTENANCE', 'NAV-WO-2026-0020', NULL, 'WORK_ORDER', 'APPROVED', 12000000.0::double precision, TIMESTAMP '2026-07-27 11:00:00', 'Mobil kompressor tirkamasi bosim regulyatori sozlandi', 'Yakuniy ish natijasi qabul qilindi', NULL, 'Transport xizmati limitiga biriktirildi', 'economist'),
        (9, 'NAV-COST-ELECTRICAL-INSTRUMENTATION', 'NAV-WO-2026-0006', NULL, 'WORK_ORDER', 'APPROVED', 11000000.0::double precision, TIMESTAMP '2026-07-28 10:00:00', 'Elektr dvigatel nazorat zanjiri bo''yicha xarajat', 'Elektr xizmati hisoboti asosida tasdiqlandi', NULL, 'Elektr va NQOA limitiga biriktirildi', 'chief'),
        (10, 'NAV-COST-ELECTRICAL-INSTRUMENTATION', 'NAV-WO-2026-0010', NULL, 'WORK_ORDER', 'APPROVED', 13000000.0::double precision, TIMESTAMP '2026-07-29 10:00:00', 'Zaxira nasos avtomatika zanjirini sozlash xarajati', 'Avtomatika zanjiri meyorga keldi', NULL, 'Elektr va NQOA limitiga biriktirildi', 'chief'),
        (11, 'NAV-COST-CONTRACTOR-SERVICES', 'NAV-WO-2026-0009', NULL, 'WORK_ORDER', 'APPROVED', 38000000.0::double precision, TIMESTAMP '2026-07-30 12:00:00', 'Quvur flanes zonasi bo''yicha ixtisoslashgan diagnostika xarajati', 'Pudratchi xulosasi va qabul hujjati asosida tasdiqlandi', NULL, 'Pudratchi xizmatlari limitiga biriktirildi', 'economist'),
        (12, 'NAV-COST-SPARE-PARTS', 'NAV-WO-2026-0007', NULL, 'WORK_ORDER', 'PENDING', 20000000.0::double precision, TIMESTAMP '2026-08-01 09:00:00', 'Boshqaruv shkafi kontaktorlari uchun ehtiyot qism xarajati kiritildi', NULL, NULL, 'Ehtiyot qismlar limitida ko''rib chiqilmoqda', NULL),
        (13, 'NAV-COST-LABOR', 'NAV-WO-2026-0008', NULL, 'WORK_ORDER', 'PENDING', 18000000.0::double precision, TIMESTAMP '2026-08-02 09:00:00', 'Dizel generator moy bosimi bo''yicha brigada mehnati kiritildi', NULL, NULL, 'Ichki mehnat xarajati ko''rib chiqilmoqda', NULL),
        (14, 'NAV-COST-VEHICLE-MAINTENANCE', 'NAV-WO-2026-0011', NULL, 'WORK_ORDER_MANUAL_WITH_REASON', 'PENDING', 16000000.0::double precision, TIMESTAMP '2026-08-03 09:00:00', 'Servis pikapi tormoz tizimi bo''yicha qo''shimcha xarajat kiritildi', NULL, NULL, 'Transport xarajati ko''rib chiqilmoqda', NULL),
        (15, 'NAV-COST-ELECTRICAL-INSTRUMENTATION', 'NAV-WO-2026-0007', NULL, 'WORK_ORDER', 'PENDING', 12000000.0::double precision, TIMESTAMP '2026-08-04 09:00:00', 'NQOA shkafi signal zanjiri bo''yicha xarajat kiritildi', NULL, NULL, 'Elektr va NQOA limitida ko''rib chiqilmoqda', NULL),
        (16, 'NAV-COST-CONTRACTOR-SERVICES', 'NAV-WO-2026-0013', NULL, 'WORK_ORDER', 'PENDING', 28000000.0::double precision, TIMESTAMP '2026-08-05 09:00:00', 'Avtokran gidravlik shlangi bo''yicha tashqi xizmat xarajati kiritildi', NULL, NULL, 'Pudratchi xizmatlari limitida ko''rib chiqilmoqda', NULL),
        (17, 'NAV-COST-CONSUMABLES', NULL, 'NAV-RR-2026-0005', 'REPAIR_REQUEST', 'REJECTED', 7000000.0::double precision, TIMESTAMP '2026-08-06 09:00:00', 'Ventilyator yuritmasi uchun sarf materiallari xarajati kiritildi', 'Biriktirilgan hujjatda miqdor va ish hajmi mos kelmadi', 'Miqdor qayta asoslanishi va ish buyrug''i bilan bog''lanishi kerak', 'Sarf materiallari limitiga vaqtincha biriktirilgan edi', 'economist'),
        (18, 'NAV-COST-CONTRACTOR-SERVICES', 'NAV-WO-2026-0002', NULL, 'WORK_ORDER_MANUAL_WITH_REASON', 'REJECTED', 24000000.0::double precision, TIMESTAMP '2026-08-07 09:00:00', 'Kompressor diagnostikasi bo''yicha qo''shimcha pudratchi xarajati kiritildi', 'Shartnoma ilovasi va bajarilgan ish hujjati yetarli emas', 'Pudratchi hujjatlari to''ldirilib qayta kiritilishi kerak', 'Pudratchi xizmatlari limitiga vaqtincha biriktirilgan edi', 'economist')
    ) AS v(idx, category_code, work_order_number, repair_request_number, source_type, status, amount, cost_date, notes, review_comment, correction_reason, allocation_comment, reviewer_key)
)
INSERT INTO actual_costs (
    id, created_at, updated_at, is_deleted,
    work_order_id, repair_request_id, contractor_work_id,
    source_type, source_id, budget_line_id, cost_category_id,
    status, reviewed_by_id, reviewed_at, review_comment,
    correction_reason, allocation_comment, allocated_by_id, allocated_at,
    amount, cost_date, notes
)
SELECT ('71000000-0000-0000-0000-00000017' || lpad(av.idx::text, 4, '0'))::uuid,
       now(), now(), false,
       wo.id, rr.id, NULL,
       av.source_type, COALESCE(wo.id, rr.id), bl.id, cc.id,
       av.status,
       CASE av.reviewer_key
           WHEN 'chief' THEN actor.chief_mechanic_id
           WHEN 'economist' THEN actor.economist_id
           ELSE NULL
       END,
       CASE WHEN av.status IN ('APPROVED', 'REJECTED') THEN av.cost_date + INTERVAL '4 hours' ELSE NULL END,
       av.review_comment, av.correction_reason, av.allocation_comment,
       actor.economist_id, av.cost_date + INTERVAL '1 hour',
       av.amount, av.cost_date, av.notes
FROM actual_values av
JOIN cost_categories cc ON cc.code = av.category_code AND cc.is_deleted = false
JOIN budget_lines bl ON bl.budget_id = '71000000-0000-0000-0000-000000150001'::uuid
                    AND bl.cost_category_id = cc.id
                    AND bl.is_deleted = false
LEFT JOIN work_orders wo ON wo.number = av.work_order_number AND wo.is_deleted = false
LEFT JOIN repair_requests rr ON rr.number = av.repair_request_number AND rr.is_deleted = false
CROSS JOIN actor
ON CONFLICT (id) DO UPDATE
SET work_order_id = EXCLUDED.work_order_id,
    repair_request_id = EXCLUDED.repair_request_id,
    contractor_work_id = EXCLUDED.contractor_work_id,
    source_type = EXCLUDED.source_type,
    source_id = EXCLUDED.source_id,
    budget_line_id = EXCLUDED.budget_line_id,
    cost_category_id = EXCLUDED.cost_category_id,
    status = EXCLUDED.status,
    reviewed_by_id = EXCLUDED.reviewed_by_id,
    reviewed_at = EXCLUDED.reviewed_at,
    review_comment = EXCLUDED.review_comment,
    correction_reason = EXCLUDED.correction_reason,
    allocation_comment = EXCLUDED.allocation_comment,
    allocated_by_id = EXCLUDED.allocated_by_id,
    allocated_at = EXCLUDED.allocated_at,
    amount = EXCLUDED.amount,
    cost_date = EXCLUDED.cost_date,
    notes = EXCLUDED.notes,
    is_deleted = false,
    updated_at = now();

WITH actor AS (
    SELECT
        (SELECT id FROM users WHERE username = 'nav.azot.economist' AND is_deleted = false) AS economist_id,
        (SELECT id FROM users WHERE username = 'nav.azot.chief.mechanic' AND is_deleted = false) AS chief_mechanic_id,
        (SELECT id FROM users WHERE username = 'nav.azot.planner' AND is_deleted = false) AS planner_id
),
nav_costs AS (
    SELECT ac.id, ac.status, ac.cost_date, ac.reviewed_by_id, ac.reviewed_at,
           right(ac.id::text, 4)::integer AS idx
    FROM actual_costs ac
    JOIN budget_lines bl ON bl.id = ac.budget_line_id
    WHERE bl.budget_id = '71000000-0000-0000-0000-000000150001'::uuid
      AND ac.id::text LIKE '71000000-0000-0000-0000-00000017%'
      AND ac.is_deleted = false
      AND bl.is_deleted = false
),
event_values AS (
    SELECT nc.idx * 10 + 1 AS event_idx, nc.id AS actual_cost_id, 'CREATED' AS event_code,
           'PENDING' AS event_status, 'Xarajat yozuvi yaratildi' AS title,
           'Xarajat moliyaviy ko''rib chiqish uchun ro''yxatga olindi' AS description,
           actor.planner_id AS actor_user_id, nc.cost_date + INTERVAL '10 minutes' AS occurred_at
    FROM nav_costs nc
    CROSS JOIN actor
    UNION ALL
    SELECT nc.idx * 10 + 2, nc.id, 'APPROVED',
           'APPROVED', 'Xarajat tasdiqlandi',
           'Xarajat byudjet liniyasiga tasdiqlangan summa sifatida kiritildi',
           COALESCE(nc.reviewed_by_id, actor.economist_id), COALESCE(nc.reviewed_at, nc.cost_date + INTERVAL '4 hours')
    FROM nav_costs nc
    CROSS JOIN actor
    WHERE nc.status = 'APPROVED'
    UNION ALL
    SELECT nc.idx * 10 + 3, nc.id, 'REJECTED',
           'REJECTED', 'Xarajat rad etildi',
           'Xarajat hujjatlari yetarli bo''lmagani uchun rad etildi',
           COALESCE(nc.reviewed_by_id, actor.economist_id), COALESCE(nc.reviewed_at, nc.cost_date + INTERVAL '4 hours')
    FROM nav_costs nc
    CROSS JOIN actor
    WHERE nc.status = 'REJECTED'
    UNION ALL
    SELECT nc.idx * 10 + 4, nc.id, 'CORRECTION_REQUESTED',
           'REJECTED', 'Tuzatish so''rovi yuborildi',
           'Masul xodimdan xarajat asoslarini to''ldirish so''raldi',
           actor.economist_id, nc.cost_date + INTERVAL '4 hours 20 minutes'
    FROM nav_costs nc
    CROSS JOIN actor
    WHERE nc.status = 'REJECTED'
)
INSERT INTO actual_cost_review_events (
    id, created_at, updated_at, is_deleted,
    actual_cost_id, notification_id, route_override_id, actor_user_id,
    source, event_group, event_code, title, description,
    severity, status, handover_comment, acknowledgement_comment, occurred_at
)
SELECT ('71000000-0000-0000-0000-00000018' || lpad(ev.event_idx::text, 4, '0'))::uuid,
       now(), now(), false,
       ev.actual_cost_id, NULL, NULL, ev.actor_user_id,
       'SYSTEM', 'REVIEW', ev.event_code, ev.title, ev.description,
       CASE WHEN ev.event_code IN ('REJECTED', 'CORRECTION_REQUESTED') THEN 'MEDIUM' ELSE NULL END,
       ev.event_status, NULL, NULL, ev.occurred_at
FROM event_values ev
ON CONFLICT (id) DO UPDATE
SET actual_cost_id = EXCLUDED.actual_cost_id,
    actor_user_id = EXCLUDED.actor_user_id,
    source = EXCLUDED.source,
    event_group = EXCLUDED.event_group,
    event_code = EXCLUDED.event_code,
    title = EXCLUDED.title,
    description = EXCLUDED.description,
    severity = EXCLUDED.severity,
    status = EXCLUDED.status,
    handover_comment = EXCLUDED.handover_comment,
    acknowledgement_comment = EXCLUDED.acknowledgement_comment,
    occurred_at = EXCLUDED.occurred_at,
    is_deleted = false,
    updated_at = now();

WITH nav_line_totals AS (
    SELECT bl.id AS budget_line_id,
           COALESCE(sum(CASE WHEN ac.status = 'APPROVED' THEN ac.amount ELSE 0 END), 0.0)::double precision AS actual_amount,
           COALESCE(sum(CASE WHEN ac.status = 'PENDING' THEN ac.amount ELSE 0 END), 0.0)::double precision AS committed_amount
    FROM budget_lines bl
    LEFT JOIN actual_costs ac ON ac.budget_line_id = bl.id
                             AND ac.is_deleted = false
    WHERE bl.budget_id = '71000000-0000-0000-0000-000000150001'::uuid
      AND bl.is_deleted = false
    GROUP BY bl.id
)
UPDATE budget_lines bl
SET actual_amount = nlt.actual_amount,
    committed_amount = nlt.committed_amount,
    updated_at = now()
FROM nav_line_totals nlt
WHERE bl.id = nlt.budget_line_id;

WITH budget_totals AS (
    SELECT bl.budget_id,
           sum(bl.planned_amount)::double precision AS total_planned,
           sum(bl.actual_amount)::double precision AS total_actual,
           sum(bl.committed_amount)::double precision AS total_committed
    FROM budget_lines bl
    WHERE bl.budget_id = '71000000-0000-0000-0000-000000150001'::uuid
      AND bl.is_deleted = false
    GROUP BY bl.budget_id
)
UPDATE maintenance_budgets mb
SET total_planned = bt.total_planned,
    total_actual = bt.total_actual,
    total_committed = bt.total_committed,
    updated_at = now()
FROM budget_totals bt
WHERE mb.id = bt.budget_id
  AND mb.is_deleted = false;
