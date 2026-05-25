-- 08. PPR plans and tasks.
INSERT INTO ppr_plans (id, created_at, updated_at, is_deleted, code, name, start_date, end_date, status, department_id, created_by_id, approved_by_id, notes)
VALUES ('00000000-0000-0000-0000-000000060001', now(), now(), false, 'DEMO-PPR-2026-05-AMM', 'Demo May 2026 PPR plan for ammonia workshop', DATE '2026-05-01', DATE '2026-05-31', 'APPROVED', '00000000-0000-0000-0000-00000000d002', '00000000-0000-0000-0000-00000000a005', '00000000-0000-0000-0000-00000000a002', 'Demo approved monthly PPR plan')
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

-- Link part of phase-2 seeded reservations to concrete requests/work orders after workflow entities exist.
WITH map AS (
    SELECT
        ('10000000-0000-0000-0000-' || '00000024' || lpad(gs::text, 4, '0'))::uuid AS reservation_id,
        ('00000000-0000-0000-0000-' || '00000008' || lpad((1 + ((gs - 1) % 50))::text, 4, '0'))::uuid AS work_order_id,
        ('00000000-0000-0000-0000-' || '00000007' || lpad((1 + ((gs - 1) % 40))::text, 4, '0'))::uuid AS repair_request_id
    FROM generate_series(1, 18) AS gs
)
UPDATE reservations r
SET work_order_id = map.work_order_id,
    repair_request_id = map.repair_request_id
FROM map
WHERE r.id = map.reservation_id
  AND r.is_deleted = false;

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


-- 17.07. Inspection routes, checkpoints, rounds, and results.
INSERT INTO inspection_routes (id, created_at, updated_at, is_deleted, code, name, department_id, frequency, target_duration_min, description, is_active)
SELECT ('00000000-0000-0000-0000-' || '0000000d' || lpad(gs::text, 4, '0'))::uuid, now(), now(), false,
       'DEMO-IR-' || (ARRAY['UREA-SHIFT','HNO3-SHIFT','MECH-DAILY','AMM-WEEKLY','UTIL-DAILY'])[gs],
       (ARRAY['Demo urea shift inspection','Demo nitric acid shift inspection','Demo mechanical daily inspection','Demo ammonia weekly reliability route','Demo utilities daily inspection'])[gs],
       ('00000000-0000-0000-0000-' || '00000000d00' || (1 + ((gs - 1) % 5))::text)::uuid,
       (ARRAY['SHIFT','SHIFT','DAILY','WEEKLY','DAILY'])[gs],
       (ARRAY[45,50,60,90,40])[gs],
       'Production-like demo inspection route',
       true
FROM generate_series(1, 5) AS gs
ON CONFLICT (code) DO NOTHING;

INSERT INTO inspection_checkpoints (id, created_at, updated_at, is_deleted, route_id, order_index, equipment_id, location_id, title, instruction, check_type, expected_min, expected_max, expected_unit, is_mandatory)
SELECT ('00000000-0000-0000-0000-' || '0000000d' || lpad((100 + gs)::text, 4, '0'))::uuid, now(), now(), false,
       ('00000000-0000-0000-0000-' || '0000000d000' || (1 + ((gs - 1) % 5))::text)::uuid,
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
FROM generate_series(1, 36) AS gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO inspection_rounds (id, created_at, updated_at, is_deleted, route_id, performed_by, started_at, completed_at, status, findings_count, alarm_count, notes)
SELECT ('00000000-0000-0000-0000-' || '0000000d' || lpad((200 + gs)::text, 4, '0'))::uuid, now(), now(), false,
       ('00000000-0000-0000-0000-' || '0000000d000' || (1 + ((gs - 1) % 5))::text)::uuid,
       '00000000-0000-0000-0000-00000000a004'::uuid,
       now() - ((gs % 18) || ' days')::interval,
       CASE WHEN gs % 5 <> 0 THEN now() - ((gs % 18) || ' days')::interval + interval '45 minutes' ELSE NULL END,
       CASE WHEN gs % 5 = 0 THEN 'IN_PROGRESS' ELSE 'COMPLETED' END,
       CASE WHEN gs % 4 = 0 THEN 2 ELSE gs % 2 END,
       CASE WHEN gs % 7 = 0 THEN 1 ELSE 0 END,
       'Production-like inspection round for demo route'
FROM generate_series(1, 24) AS gs
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

-- 17.03.X Maintenance workflow supplements.
INSERT INTO maintenance_templates (id, created_at, updated_at, is_deleted, code, name, description, equipment_type_id, maintenance_kind, normative_labor_hours, is_active)
SELECT ('10000000-0000-0000-0000-' || '00000031' || lpad(gs::text, 4, '0'))::uuid,
       now(), now(), false,
       'MNT-TPL-' || lpad(gs::text, 3, '0'),
       (ARRAY['Pump preventive package','Compressor vibration package','Reactor inspection package','Heat exchanger cleaning package','Valve overhaul package'])[1 + ((gs - 1) % 5)] || ' ' || lpad(gs::text, 3, '0'),
       'Template for planned and condition-based maintenance',
       (ARRAY['00000000-0000-0000-0000-00000000f001','00000000-0000-0000-0000-00000000f002','00000000-0000-0000-0000-00000000f003','00000000-0000-0000-0000-00000000f004','00000000-0000-0000-0000-00000000f005'])[1 + ((gs - 1) % 5)]::uuid,
       (ARRAY['PREVENTIVE','INSPECTION','OVERHAUL','PREDICTIVE','CURRENT_REPAIR'])[1 + ((gs - 1) % 5)],
       (ARRAY[4.0,8.0,16.0,6.0,12.0])[1 + ((gs - 1) % 5)],
       true
FROM generate_series(1, 18) AS gs
ON CONFLICT (code) DO NOTHING;

INSERT INTO maintenance_operations (id, created_at, updated_at, is_deleted, template_id, sequence, name, description, duration_hours, required_skill, control_parameter, control_min, control_max, control_unit, tools_required, spare_parts_required, consumables_required, safety_notes, instruction_url)
SELECT ('10000000-0000-0000-0000-' || '00000032' || lpad((row_number() OVER (ORDER BY t.id, gs))::text, 4, '0'))::uuid,
       now(), now(), false,
       t.id,
       gs,
       (ARRAY['Isolation and LOTO','Disassembly','Inspection','Replacement/adjustment','Assembly and test'])[gs],
       'Standard maintenance operation step',
       (ARRAY[1.0,2.0,3.0,2.0,1.5])[gs],
       (ARRAY['FOREMAN','MECHANIC','INSPECTOR','MECHANIC','FOREMAN'])[gs],
       CASE WHEN gs IN (3,5) THEN 'vibration' ELSE NULL END,
       CASE WHEN gs = 3 THEN 0 ELSE NULL END,
       CASE WHEN gs = 3 THEN 7.1 ELSE NULL END,
       CASE WHEN gs = 3 THEN 'mm/s' ELSE NULL END,
       'Standard toolbox',
       CASE WHEN gs IN (2,4) THEN 'bearing,seals,gaskets' ELSE NULL END,
       CASE WHEN gs IN (2,4) THEN 'oil,cleaner' ELSE NULL END,
       'Follow safety permit and PPE instructions',
       'https://demo-maintenance.local/op/' || gs
FROM maintenance_templates t
CROSS JOIN generate_series(1, 5) AS gs
WHERE t.code LIKE 'MNT-TPL-%'
ON CONFLICT (id) DO NOTHING;

INSERT INTO maintenance_regulation_attribute_conditions (id, created_at, updated_at, is_deleted, regulation_id, attribute_key, operator, value_number, value_text, value_option, value_boolean, value_date)
SELECT ('10000000-0000-0000-0000-' || '00000033' || lpad(gs::text, 4, '0'))::uuid,
       now(), now(), false,
       ('00000000-0000-0000-0000-' || '00000002' || lpad((1 + ((gs - 1) % 8))::text, 4, '0'))::uuid,
       (ARRAY['operating_pressure','vibration_limit','cooling_mode','lubrication_type'])[1 + ((gs - 1) % 4)],
       (ARRAY['GREATER_THAN','LESS_THAN_OR_EQUALS','EQUALS','EXISTS'])[1 + ((gs - 1) % 4)],
       CASE WHEN gs % 4 IN (1,2) THEN 4 + gs ELSE NULL END,
       CASE WHEN gs % 4 = 3 THEN 'WATER' ELSE NULL END,
       CASE WHEN gs % 4 = 0 THEN 'OIL_ISO46' ELSE NULL END,
       NULL,
       NULL
FROM generate_series(1, 20) AS gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO planned_shutdowns (id, created_at, updated_at, is_deleted, department_id, name, reason, status, start_at, end_at)
SELECT ('10000000-0000-0000-0000-' || '00000034' || lpad(gs::text, 4, '0'))::uuid,
       now(), now(), false,
       ('00000000-0000-0000-0000-' || '00000000d00' || (1 + ((gs - 1) % 5))::text)::uuid,
       'Planned shutdown window #' || lpad(gs::text, 3, '0'),
       (ARRAY['Annual overhaul','Catalyst replacement','Heat exchanger cleaning','Instrumentation recalibration','Electrical switchgear maintenance'])[1 + ((gs - 1) % 5)],
       (ARRAY['DRAFT','GENERATED','APPROVED','IN_PROGRESS','CLOSED'])[1 + ((gs - 1) % 5)],
       now() + ((gs * 7) || ' days')::interval,
       now() + ((gs * 7 + 2) || ' days')::interval
FROM generate_series(1, 15) AS gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO repair_campaigns (id, created_at, updated_at, is_deleted, code, name, scope, notes, status, year, quarter, department_id, start_date, end_date, total_budget, total_actual)
SELECT ('10000000-0000-0000-0000-' || '00000035' || lpad(gs::text, 4, '0'))::uuid,
       now(), now(), false,
       'RCM-CAMP-' || lpad(gs::text, 3, '0'),
       'Repair campaign ' || lpad(gs::text, 3, '0'),
       'Rotating and static equipment reliability campaign',
       'Demo campaign for annual maintenance planning',
       (ARRAY['DRAFT','APPROVED','IN_PROGRESS','COMPLETED','CLOSED'])[1 + ((gs - 1) % 5)],
       2026, 1 + (gs % 4),
       ('00000000-0000-0000-0000-' || '00000000d00' || (1 + ((gs - 1) % 5))::text)::uuid,
       DATE '2026-01-01' + (gs * 10),
       DATE '2026-02-01' + (gs * 10),
       12000000 + (gs * 450000),
       6400000 + (gs * 210000)
FROM generate_series(1, 15) AS gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO repair_campaign_stages (id, created_at, updated_at, is_deleted, campaign_id, sequence, name, status, start_date, end_date, planned_cost, actual_cost, notes)
SELECT ('10000000-0000-0000-0000-' || '00000036' || lpad((row_number() OVER (ORDER BY c.id, s.seq))::text, 4, '0'))::uuid,
       now(), now(), false,
       c.id,
       s.seq,
       s.name,
       (ARRAY['DRAFT','APPROVED','IN_PROGRESS','COMPLETED'])[1 + ((s.seq - 1) % 4)],
       c.start_date + ((s.seq - 1) * 5),
       c.start_date + ((s.seq - 1) * 5) + 4,
       900000 + (s.seq * 250000),
       600000 + (s.seq * 180000),
       'Stage plan for repair campaign'
FROM repair_campaigns c
CROSS JOIN (VALUES (1,'Preparation'),(2,'Execution'),(3,'Verification')) AS s(seq, name)
WHERE c.code LIKE 'RCM-CAMP-%'
ON CONFLICT (id) DO NOTHING;

INSERT INTO sla_rules (id, created_at, updated_at, is_deleted, code, name, entity_type, trigger_type, threshold_hours, department_id, is_active)
SELECT ('10000000-0000-0000-0000-' || '00000037' || lpad(gs::text, 4, '0'))::uuid,
       now(), now(), false,
       'SLA-DEMO-' || lpad(gs::text, 3, '0'),
       'Demo SLA rule ' || lpad(gs::text, 3, '0'),
       (ARRAY['REPAIR_REQUEST','PPR_TASK','WORK_ORDER','DEFECT'])[1 + ((gs - 1) % 4)],
       (ARRAY['REQUEST_EMERGENCY','REQUEST_OVERDUE','PPR_OVERDUE','WORK_ORDER_OVERDUE','DEFECT_REPEAT'])[1 + ((gs - 1) % 5)],
       4 + (gs % 24),
       ('00000000-0000-0000-0000-' || '00000000d00' || (1 + ((gs - 1) % 5))::text)::uuid,
       true
FROM generate_series(1, 15) AS gs
ON CONFLICT (code) DO NOTHING;

INSERT INTO safety_permits (id, created_at, updated_at, is_deleted, work_order_id, permit_number, status, issued_by_id, issued_at, valid_until, notes)
SELECT ('10000000-0000-0000-0000-' || '00000038' || lpad(gs::text, 4, '0'))::uuid,
       now(), now(), false,
       ('00000000-0000-0000-0000-' || '00000008' || lpad((1 + ((gs - 1) % 50))::text, 4, '0'))::uuid,
       'SP-' || lpad(gs::text, 5, '0'),
       (ARRAY['DRAFT','ISSUED','CLOSED'])[1 + ((gs - 1) % 3)],
       '00000000-0000-0000-0000-00000000a003'::uuid,
       now() - ((gs % 10) || ' days')::interval,
       now() + ((1 + (gs % 3)) || ' days')::interval,
       'Safety permit generated for demo work execution'
FROM generate_series(1, 24) AS gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO completion_acts (id, created_at, updated_at, is_deleted, work_order_id, act_number, signed_by_id, signed_at, summary)
SELECT ('10000000-0000-0000-0000-' || '00000039' || lpad(gs::text, 4, '0'))::uuid,
       now(), now(), false,
       ('00000000-0000-0000-0000-' || '00000008' || lpad((1 + ((gs - 1) % 50))::text, 4, '0'))::uuid,
       'ACT-2026-' || lpad(gs::text, 5, '0'),
       '00000000-0000-0000-0000-00000000a002'::uuid,
       now() - ((gs % 20) || ' days')::interval,
       'Completed maintenance with acceptance measurements'
FROM generate_series(1, 24) AS gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO downtime_events (id, created_at, updated_at, is_deleted, equipment_id, department_id, type, start_at, end_at, duration_minutes, work_order_id, description)
SELECT ('10000000-0000-0000-0000-' || '0000003a' || lpad(gs::text, 4, '0'))::uuid,
       now(), now(), false,
       ('00000000-0000-0000-0000-' || '00000001' || lpad((1 + ((gs - 1) % 60))::text, 4, '0'))::uuid,
       ('00000000-0000-0000-0000-' || '00000000d00' || (1 + ((gs - 1) % 5))::text)::uuid,
       (ARRAY['PLANNED','UNPLANNED','EMERGENCY'])[1 + ((gs - 1) % 3)],
       now() - ((gs % 30) || ' days')::interval,
       now() - ((gs % 30) || ' days')::interval + ((2 + (gs % 6)) || ' hours')::interval,
       (2 + (gs % 6)) * 60,
       ('00000000-0000-0000-0000-' || '00000008' || lpad((1 + ((gs - 1) % 50))::text, 4, '0'))::uuid,
       'Downtime event seeded for reliability analytics'
FROM generate_series(1, 24) AS gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO reliability_metrics (id, created_at, updated_at, is_deleted, equipment_id, metric_date, mtbf_hours, mttr_hours, availability, failure_rate)
SELECT ('10000000-0000-0000-0000-' || '0000003b' || lpad(gs::text, 4, '0'))::uuid,
       now(), now(), false,
       ('00000000-0000-0000-0000-' || '00000001' || lpad((1 + ((gs - 1) % 60))::text, 4, '0'))::uuid,
       DATE '2026-05-01' + (gs % 25),
       1200 + (gs * 37),
       3 + (gs % 9),
       0.82 + ((gs % 15) * 0.01),
       0.01 + ((gs % 8) * 0.003)
FROM generate_series(1, 24) AS gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO oee_records (id, created_at, updated_at, is_deleted, equipment_id, shift_start, shift_end, planned_production_minutes, run_minutes, total_count, good_count, ideal_cycle_seconds, availability, performance, quality, oee, notes)
SELECT ('10000000-0000-0000-0000-' || '0000003c' || lpad(gs::text, 4, '0'))::uuid,
       now(), now(), false,
       ('00000000-0000-0000-0000-' || '00000001' || lpad((1 + ((gs - 1) % 60))::text, 4, '0'))::uuid,
       timestamp '2026-05-01 08:00:00' + ((gs - 1) || ' days')::interval,
       timestamp '2026-05-01 20:00:00' + ((gs - 1) || ' days')::interval,
       720, 640 - (gs % 25), 1000 + (gs * 25), 940 + (gs * 22), 32,
       0.85 + ((gs % 7) * 0.01),
       0.88 + ((gs % 6) * 0.01),
       0.93 + ((gs % 5) * 0.01),
       0.70 + ((gs % 9) * 0.015),
       'OEE seeded for dashboard trend'
FROM generate_series(1, 24) AS gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO rcm_snapshots (id, created_at, updated_at, is_deleted, equipment_id, captured_at, equipment_code, equipment_name, mtbf_hours, mttr_hours, probability, consequence, risk_score, open_defects, criticality_class, repair_priority)
SELECT ('10000000-0000-0000-0000-' || '0000003d' || lpad(gs::text, 4, '0'))::uuid,
       now(), now(), false,
       eq.id,
       now() - ((gs % 20) || ' days')::interval,
       eq.code,
       eq.name,
       900 + (gs * 40),
       2 + (gs % 8),
       1 + (gs % 5),
       2 + (gs % 4),
       (1 + (gs % 5)) * (2 + (gs % 4)),
       gs % 6,
       'A',
       1 + (gs % 5)
FROM (
    SELECT id, code, name, row_number() OVER (ORDER BY code) AS rn
    FROM equipment
    WHERE is_deleted = false
      AND code LIKE 'DEMO-%'
) eq
JOIN generate_series(1, 20) gs ON gs = eq.rn
ON CONFLICT (id) DO NOTHING;

INSERT INTO escalation_events (id, created_at, updated_at, is_deleted, entity_type, entity_id, trigger_type, status, sla_rule_id, raised_at, acknowledged_by_id, acknowledged_at, resolved_by_id, resolved_at, notes)
SELECT ('10000000-0000-0000-0000-' || '0000003e' || lpad(gs::text, 4, '0'))::uuid,
       now(), now(), false,
       (ARRAY['REPAIR_REQUEST','PPR_TASK','WORK_ORDER','DEFECT'])[1 + ((gs - 1) % 4)],
       (ARRAY['DEMO-RR-2026-0001','DEMO-PPR-TASK-001','DEMO-WO-2026-0001','DEMO-DEF-2026-0001'])[1 + ((gs - 1) % 4)],
       (ARRAY['REQUEST_OVERDUE','PPR_OVERDUE','WORK_ORDER_OVERDUE','DEFECT_REPEAT'])[1 + ((gs - 1) % 4)],
       (ARRAY['OPEN','ACKNOWLEDGED','RESOLVED'])[1 + ((gs - 1) % 3)],
       ('10000000-0000-0000-0000-' || '00000037' || lpad((1 + ((gs - 1) % 15))::text, 4, '0'))::uuid,
       now() - ((gs % 18) || ' hours')::interval,
       CASE WHEN gs % 3 IN (2, 0) THEN '00000000-0000-0000-0000-00000000a002'::uuid ELSE NULL END,
       CASE WHEN gs % 3 IN (2, 0) THEN now() - ((gs % 12) || ' hours')::interval ELSE NULL END,
       CASE WHEN gs % 3 = 0 THEN '00000000-0000-0000-0000-00000000a001'::uuid ELSE NULL END,
       CASE WHEN gs % 3 = 0 THEN now() - ((gs % 6) || ' hours')::interval ELSE NULL END,
       'Escalation event generated from demo SLA logic'
FROM generate_series(1, 24) AS gs
ON CONFLICT (id) DO NOTHING;

