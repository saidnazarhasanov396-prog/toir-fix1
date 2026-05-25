-- 12. Procurement.
INSERT INTO procurement_requests (id, created_at, updated_at, is_deleted, number, title, description, department_id, warehouse_id, requested_by, approved_by, status, source, required_by, total_estimated_cost, submitted_at, approved_at, ordered_at, received_at, rejection_reason)
VALUES ('00000000-0000-0000-0000-0000000a0001', now(), now(), false, 'NAV-PR-2026-0001', 'Replenish low stock mechanical seals', 'Generated from low stock level in central warehouse.', '00000000-0000-0000-0000-00000000d001', '00000000-0000-0000-0000-000000030001', '00000000-0000-0000-0000-00000000a007', '00000000-0000-0000-0000-00000000a002', 'APPROVED', 'AUTO', DATE '2026-05-30', 1960000.00, now() - interval '1 day', now() - interval '12 hours', NULL, NULL, NULL)
ON CONFLICT (number) DO NOTHING;

INSERT INTO procurement_request_lines (id, created_at, updated_at, is_deleted, request_id, spare_part_id, quantity, unit, unit_price, estimated_cost, notes)
VALUES ('00000000-0000-0000-0000-0000000a0101', now(), now(), false, '00000000-0000-0000-0000-0000000a0001', '00000000-0000-0000-0000-000000040002', 8, 'pc', 245000.00, 1960000.00, 'Bring stock above reorder point')
ON CONFLICT (id) DO NOTHING;

-- 13. Finance.
INSERT INTO maintenance_budgets (id, created_at, updated_at, is_deleted, year, month, department_id, status, total_planned, total_actual)
VALUES ('00000000-0000-0000-0000-0000000b0001', now(), now(), false, 2026, 5, '00000000-0000-0000-0000-00000000d002', 'APPROVED', 50000000.00, 245000.00)
ON CONFLICT (id) DO NOTHING;

INSERT INTO budget_lines (id, created_at, updated_at, is_deleted, budget_id, cost_category_id, description, planned_amount, actual_amount)
VALUES ('00000000-0000-0000-0000-0000000b0101', now(), now(), false, '00000000-0000-0000-0000-0000000b0001', '00000000-0000-0000-0000-00000000c101', 'Navoiyazot ammonia spare part budget', 35000000.00, 245000.00)
ON CONFLICT (id) DO NOTHING;

INSERT INTO actual_costs (id, created_at, updated_at, is_deleted, work_order_id, repair_request_id, contractor_work_id, budget_line_id, cost_category_id, status, reviewed_by_id, reviewed_at, review_comment, amount, cost_date, notes)
VALUES ('00000000-0000-0000-0000-0000000b0201', now(), now(), false, '00000000-0000-0000-0000-000000080001', '00000000-0000-0000-0000-000000070001', NULL, '00000000-0000-0000-0000-0000000b0101', '00000000-0000-0000-0000-00000000c101', 'APPROVED', '00000000-0000-0000-0000-00000000a008', now() - interval '1 hour', 'Approved for industrial work order material issue', 245000.00, now() - interval '2 hours', 'Mechanical seal issued to work order')
ON CONFLICT (id) DO NOTHING;

-- 14. Approvals.
INSERT INTO approval_requests (id, created_at, updated_at, is_deleted, document_type, document_id, title, requester_id, status, current_step, completed_at, description)
VALUES ('00000000-0000-0000-0000-0000000c0001', now(), now(), false, 'PROCUREMENT_REQUEST', '00000000-0000-0000-0000-0000000a0001', 'Approve industrial low-stock procurement request', '00000000-0000-0000-0000-00000000a007', 'PENDING', 1, NULL, 'Navoiyazot approval awaiting chief mechanic review')
ON CONFLICT (id) DO NOTHING;

INSERT INTO approval_steps (id, created_at, updated_at, is_deleted, request_id, step_number, approver_id, approver_role, decision, decided_at, comment)
VALUES
('00000000-0000-0000-0000-0000000c0101', now(), now(), false, '00000000-0000-0000-0000-0000000c0001', 1, '00000000-0000-0000-0000-00000000a002', 'CHIEF_MECHANIC', 'PENDING', NULL, NULL),
('00000000-0000-0000-0000-0000000c0102', now(), now(), false, '00000000-0000-0000-0000-0000000c0001', 2, '00000000-0000-0000-0000-00000000a001', 'TECHNICAL_DIRECTOR', 'PENDING', NULL, NULL)
ON CONFLICT (request_id, step_number) DO NOTHING;

-- 15. Inspections.
INSERT INTO inspection_routes (id, created_at, updated_at, is_deleted, code, name, department_id, frequency, target_duration_min, description, is_active)
VALUES ('00000000-0000-0000-0000-0000008d0001', now(), now(), false, 'NAV-IR-AMM-SHIFT', 'Navoiyazot ammonia shift inspection', '00000000-0000-0000-0000-00000000d002', 'SHIFT', 45, 'Shift inspection route for ammonia compressor area', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO inspection_checkpoints (id, created_at, updated_at, is_deleted, route_id, order_index, equipment_id, location_id, title, instruction, check_type, expected_min, expected_max, expected_unit, is_mandatory)
VALUES
('00000000-0000-0000-0000-0000008d0101', now(), now(), false, '00000000-0000-0000-0000-0000008d0001', 1, '00000000-0000-0000-0000-000000010001', '00000000-0000-0000-0000-00000000a102', 'Check compressor vibration', 'Measure vibration on drive-end bearing.', 'MEASUREMENT', 0, 4.5, 'mm/s', true),
('00000000-0000-0000-0000-0000008d0102', now(), now(), false, '00000000-0000-0000-0000-0000008d0001', 2, '00000000-0000-0000-0000-000000010002', '00000000-0000-0000-0000-00000000a102', 'Inspect pump seal area', 'Check leakage and bearing temperature.', 'VISUAL', NULL, NULL, NULL, true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO inspection_rounds (id, created_at, updated_at, is_deleted, route_id, performed_by, started_at, completed_at, status, findings_count, alarm_count, notes)
VALUES ('00000000-0000-0000-0000-0000008d0201', now(), now(), false, '00000000-0000-0000-0000-0000008d0001', '00000000-0000-0000-0000-00000000a004', now() - interval '6 hours', now() - interval '5 hours', 'COMPLETED', 1, 1, 'Navoiyazot completed shift round')
ON CONFLICT (id) DO NOTHING;

INSERT INTO inspection_round_results (id, created_at, updated_at, is_deleted, round_id, checkpoint_id, status, measured_value, measured_unit, comment, defect_id, photo_file_ids)
VALUES
('00000000-0000-0000-0000-0000008d0301', now(), now(), false, '00000000-0000-0000-0000-0000008d0201', '00000000-0000-0000-0000-0000008d0101', 'WARN', 4.8, 'mm/s', 'Above warning threshold, defect registered.', '00000000-0000-0000-0000-000000090001', '[]'::jsonb),
('00000000-0000-0000-0000-0000008d0302', now(), now(), false, '00000000-0000-0000-0000-0000008d0201', '00000000-0000-0000-0000-0000008d0102', 'OK', NULL, NULL, 'No visible external leakage after isolation.', NULL, '[]'::jsonb)
ON CONFLICT (id) DO NOTHING;

-- 16. Knowledge.
INSERT INTO knowledge_articles (id, created_at, updated_at, is_deleted, code, title, kind, equipment_type_id, equipment_id, defect_id, work_order_id, problem, root_cause, solution, preventive_actions, tags, author_id, view_count)
VALUES
('00000000-0000-0000-0000-0000000e0001', now(), now(), false, 'NAV-KB-COMP-VIBRATION', 'Compressor vibration early response', 'LESSON_LEARNED', '00000000-0000-0000-0000-00000000f002', '00000000-0000-0000-0000-000000010001', '00000000-0000-0000-0000-000000090001', '00000000-0000-0000-0000-000000080002', 'Vibration warning can develop into bearing failure if not investigated during the same shift.', 'Insufficient lubrication and delayed trend review.', 'Create high-priority inspection task, verify lubrication route, and plan bearing inspection.', 'Weekly vibration trend review and lubrication checklist verification.', '["industrial","compressor","vibration","bearing"]'::jsonb, '00000000-0000-0000-0000-00000000a009', 12),
('00000000-0000-0000-0000-0000000e0002', now(), now(), false, 'NAV-KB-PUMP-SEAL', 'Pump mechanical seal replacement checklist', 'PROCEDURE', '00000000-0000-0000-0000-00000000f001', '00000000-0000-0000-0000-000000010002', NULL, '00000000-0000-0000-0000-000000080001', 'Seal leakage caused product loss and bearing contamination risk.', 'Seal wear under unstable operating mode.', 'Isolate pump, replace seal cartridge, verify shaft sleeve, run leak test.', 'Keep minimum seal stock and inspect during monthly PPR.', '["industrial","pump","seal","procedure"]'::jsonb, '00000000-0000-0000-0000-00000000a002', 8)
ON CONFLICT (code) DO NOTHING;

-- 17. Production-like industrial volume expansion.
-- Set-based inserts keep the dataset deterministic while avoiding hundreds of hand-written rows.

-- 17.05. Procurement, budgets, and actual costs.
INSERT INTO procurement_requests (id, created_at, updated_at, is_deleted, number, title, description, department_id, warehouse_id, requested_by, approved_by, status, source, required_by, total_estimated_cost, submitted_at, approved_at, ordered_at, received_at, rejection_reason)
SELECT ('00000000-0000-0000-0000-' || '0000000a' || lpad(gs::text, 4, '0'))::uuid, now(), now(), false,
       'NAV-PR-2026-' || lpad(gs::text, 4, '0'),
       (ARRAY['Replenish critical rotating equipment spares','Purchase inspection consumables','Order planned outage materials','Emergency stock refill','Instrumentation spare parts request'])[1 + ((gs - 1) % 5)],
       'Production-like procurement request for industrial warehouse and maintenance flows.',
       ('00000000-0000-0000-0000-' || '00000000d00' || (1 + ((gs - 1) % 5))::text)::uuid,
       ('00000000-0000-0000-0000-' || '00000003000' || (1 + ((gs - 1) % 3))::text)::uuid,
       '00000000-0000-0000-0000-00000000a007'::uuid,
       CASE WHEN gs % 6 IN (2, 3, 4) THEN '00000000-0000-0000-0000-00000000a002'::uuid ELSE NULL END,
       (ARRAY['DRAFT','SUBMITTED','APPROVED','ORDERED','RECEIVED','CANCELLED'])[1 + ((gs - 1) % 6)],
       CASE WHEN gs % 4 = 0 THEN 'AUTO' ELSE 'MANUAL' END,
       DATE '2026-06-01' + (gs % 20),
       1200000 + (gs * 275000),
       CASE WHEN gs % 6 <> 0 THEN now() - ((gs % 7) || ' days')::interval ELSE NULL END,
       CASE WHEN gs % 6 IN (2, 3, 4) THEN now() - ((gs % 5) || ' days')::interval ELSE NULL END,
       CASE WHEN gs % 6 IN (3, 4) THEN now() - ((gs % 4) || ' days')::interval ELSE NULL END,
       CASE WHEN gs % 6 = 4 THEN now() - ((gs % 3) || ' days')::interval ELSE NULL END,
       CASE WHEN gs % 6 = 5 THEN 'Cancelled after duplicate warehouse request review' ELSE NULL END
FROM generate_series(2, 12) AS gs
ON CONFLICT (number) DO NOTHING;

INSERT INTO procurement_request_lines (id, created_at, updated_at, is_deleted, request_id, spare_part_id, quantity, unit, unit_price, estimated_cost, notes)
SELECT ('00000000-0000-0000-0000-' || '0000000a' || lpad((100 + gs)::text, 4, '0'))::uuid, now(), now(), false,
       ('00000000-0000-0000-0000-' || '0000000a' || lpad((1 + ((gs - 1) % 12))::text, 4, '0'))::uuid,
       ('00000000-0000-0000-0000-' || '00000004' || lpad((1 + ((gs - 1) % 60))::text, 4, '0'))::uuid,
       (2 + (gs % 10)),
       (ARRAY['pc','set','l','kg'])[1 + ((gs - 1) % 4)],
       90000 + (gs * 12500),
       (2 + (gs % 10)) * (90000 + (gs * 12500)),
       'Navoiyazot procurement line linked to warehouse stock planning'
FROM generate_series(2, 36) AS gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO stock_movements (id, created_at, updated_at, is_deleted, warehouse_id, spare_part_id, work_order_id, type, quantity, unit_cost, document_number, created_by_id, occurred_at, notes)
SELECT ('00000000-0000-0000-0000-' || '000000052' || lpad(row_number() OVER (ORDER BY pr.number, prl.id)::text, 3, '0'))::uuid,
       now(), now(), false,
       pr.warehouse_id, prl.spare_part_id, NULL, 'RECEIPT', prl.quantity, prl.unit_price,
       'NAV-SM-RECEIPT-' || pr.number || '-' || row_number() OVER (PARTITION BY pr.id ORDER BY prl.id),
       '00000000-0000-0000-0000-00000000a007'::uuid,
       COALESCE(pr.received_at, now() - interval '1 day'),
       'Receipt movement for received industrial procurement request'
FROM procurement_requests pr
JOIN procurement_request_lines prl ON prl.request_id = pr.id AND prl.is_deleted = false
WHERE pr.number LIKE 'NAV-PR-2026-%'
  AND pr.status = 'RECEIVED'
  AND pr.warehouse_id IS NOT NULL
  AND pr.is_deleted = false
ON CONFLICT (id) DO NOTHING;

INSERT INTO stock_movements (id, created_at, updated_at, is_deleted, warehouse_id, spare_part_id, work_order_id, type, quantity, unit_cost, document_number, created_by_id, occurred_at, notes)
SELECT ('00000000-0000-0000-0000-' || '000000053' || lpad(gs::text, 3, '0'))::uuid, now(), now(), false,
       ('00000000-0000-0000-0000-' || '00000003000' || (1 + ((gs - 1) % 3))::text)::uuid,
       ('00000000-0000-0000-0000-' || '00000004' || lpad((1 + ((gs - 1) % 50))::text, 4, '0'))::uuid,
       CASE WHEN gs % 5 IN (0, 1) THEN ('00000000-0000-0000-0000-' || '00000008' || lpad((1 + ((gs - 1) % 50))::text, 4, '0'))::uuid ELSE NULL END,
       (ARRAY['ISSUE','RESERVATION','RELEASE','ADJUSTMENT'])[1 + ((gs - 1) % 4)],
       CASE WHEN gs % 4 = 0 THEN 1 ELSE 1 + (gs % 5) END,
       65000 + (gs * 3000),
       'NAV-SM-2026-' || lpad((100 + gs)::text, 4, '0'),
       '00000000-0000-0000-0000-00000000a006'::uuid,
       now() - ((gs % 30) || ' days')::interval,
       'Production-like industrial stock movement history'
FROM generate_series(1, 60) AS gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO maintenance_budgets (id, created_at, updated_at, is_deleted, year, month, department_id, status, total_planned, total_actual)
VALUES
('00000000-0000-0000-0000-0000000b0002', now(), now(), false, 2026, 6, '00000000-0000-0000-0000-00000000d003', 'APPROVED', 72000000.00, 18800000.00),
('00000000-0000-0000-0000-0000000b0003', now(), now(), false, 2026, 6, '00000000-0000-0000-0000-00000000d004', 'DRAFT', 46000000.00, 6400000.00)
ON CONFLICT (id) DO NOTHING;

INSERT INTO budget_lines (id, created_at, updated_at, is_deleted, budget_id, cost_category_id, description, planned_amount, actual_amount)
SELECT ('00000000-0000-0000-0000-' || '0000000b' || lpad((100 + gs)::text, 4, '0'))::uuid, now(), now(), false,
       ('00000000-0000-0000-0000-' || '0000000b000' || (1 + ((gs - 1) % 3))::text)::uuid,
       ('00000000-0000-0000-0000-' || '00000000c10' || (1 + ((gs - 1) % 5))::text)::uuid,
       (ARRAY['Spare parts planned budget','Contractor repair reserve','Inspection material budget','Emergency repair reserve','PPR labor budget'])[1 + ((gs - 1) % 5)] || ' ' || lpad(gs::text, 3, '0'),
       1500000 + (gs * 420000), 200000 + (gs * 95000)
FROM generate_series(2, 18) AS gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO actual_costs (id, created_at, updated_at, is_deleted, work_order_id, repair_request_id, contractor_work_id, budget_line_id, cost_category_id, status, reviewed_by_id, reviewed_at, review_comment, amount, cost_date, notes)
SELECT ('00000000-0000-0000-0000-' || '0000000b' || lpad((200 + gs)::text, 4, '0'))::uuid, now(), now(), false,
       ('00000000-0000-0000-0000-' || '00000008' || lpad((1 + ((gs - 1) % 50))::text, 4, '0'))::uuid,
       CASE WHEN gs % 3 = 0 THEN ('00000000-0000-0000-0000-' || '00000007' || lpad((1 + ((gs - 1) % 40))::text, 4, '0'))::uuid ELSE NULL END,
       NULL,
       ('00000000-0000-0000-0000-' || '0000000b' || lpad((100 + (1 + ((gs - 1) % 18)))::text, 4, '0'))::uuid,
       ('00000000-0000-0000-0000-' || '00000000c10' || (1 + ((gs - 1) % 5))::text)::uuid,
       (ARRAY['PENDING','APPROVED','REJECTED','APPROVED'])[1 + ((gs - 1) % 4)],
       CASE WHEN gs % 4 <> 0 THEN '00000000-0000-0000-0000-00000000a008'::uuid ELSE NULL END,
       CASE WHEN gs % 4 <> 0 THEN now() - ((gs % 10) || ' days')::interval ELSE NULL END,
       CASE WHEN gs % 4 = 2 THEN 'Approved within industrial finance route' WHEN gs % 4 = 3 THEN 'Rejected pending corrected invoice' ELSE NULL END,
       180000 + (gs * 65000),
       DATE '2026-05-01' + (gs % 25),
       'Production-like actual cost for industrial maintenance accounting'
FROM generate_series(2, 30) AS gs
ON CONFLICT (id) DO NOTHING;

-- 17.06. Generic approvals.
INSERT INTO approval_requests (id, created_at, updated_at, is_deleted, document_type, document_id, title, requester_id, status, current_step, completed_at, description)
SELECT ('00000000-0000-0000-0000-' || '0000000c' || lpad(gs::text, 4, '0'))::uuid, now(), now(), false,
       (ARRAY['PROCUREMENT_REQUEST','WORK_ORDER','ACTUAL_COST','BUDGET'])[1 + ((gs - 1) % 4)],
       CASE ((gs - 1) % 4)
           WHEN 0 THEN ('00000000-0000-0000-0000-' || '0000000a' || lpad((1 + ((gs - 1) % 12))::text, 4, '0'))::uuid
           WHEN 1 THEN ('00000000-0000-0000-0000-' || '00000008' || lpad((1 + ((gs - 1) % 50))::text, 4, '0'))::uuid
           WHEN 2 THEN ('00000000-0000-0000-0000-' || '0000000b' || lpad((200 + (1 + ((gs - 1) % 30)))::text, 4, '0'))::uuid
           ELSE ('00000000-0000-0000-0000-' || '0000000b000' || (1 + ((gs - 1) % 3))::text)::uuid
       END,
       'Navoiyazot approval route ' || lpad(gs::text, 3, '0'),
       '00000000-0000-0000-0000-00000000a007'::uuid,
       (ARRAY['PENDING','APPROVED','REJECTED','PENDING'])[1 + ((gs - 1) % 4)],
       CASE WHEN gs % 4 IN (1, 0) THEN 1 ELSE 2 END,
       CASE WHEN gs % 4 IN (2, 3) THEN now() - ((gs % 6) || ' days')::interval ELSE NULL END,
       'Production-like approval for industrial maintenance governance'
FROM generate_series(2, 12) AS gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO approval_steps (id, created_at, updated_at, is_deleted, request_id, step_number, approver_id, approver_role, decision, decided_at, comment)
SELECT ('00000000-0000-0000-0000-' || '0000000c' || lpad((300 + row_number() OVER (ORDER BY req.gs, step.step_number))::text, 4, '0'))::uuid,
       now(), now(), false,
       ('00000000-0000-0000-0000-' || '0000000c' || lpad(req.gs::text, 4, '0'))::uuid,
       step.step_number,
       CASE WHEN step.step_number = 1 THEN '00000000-0000-0000-0000-00000000a002'::uuid ELSE '00000000-0000-0000-0000-00000000a001'::uuid END,
       CASE WHEN step.step_number = 1 THEN 'CHIEF_MECHANIC' ELSE 'TECHNICAL_DIRECTOR' END,
       CASE
           WHEN req.gs % 4 = 1 AND step.step_number = 1 THEN 'PENDING'
           WHEN req.gs % 4 = 2 THEN 'APPROVED'
           WHEN req.gs % 4 = 3 AND step.step_number = 1 THEN 'REJECTED'
           WHEN req.gs % 4 = 0 AND step.step_number = 1 THEN 'APPROVED'
           ELSE 'PENDING'
       END,
       CASE
           WHEN req.gs % 4 IN (2, 3) OR (req.gs % 4 = 0 AND step.step_number = 1) THEN now() - ((req.gs % 5) || ' days')::interval
           ELSE NULL
       END,
       CASE WHEN req.gs % 4 = 3 AND step.step_number = 1 THEN 'Rejected in industrial route for correction' ELSE NULL END
FROM generate_series(2, 12) AS req(gs)
CROSS JOIN (VALUES (1), (2)) AS step(step_number)
ON CONFLICT (request_id, step_number) DO NOTHING;


-- 17.08. Knowledge base.
INSERT INTO knowledge_articles (id, created_at, updated_at, is_deleted, code, title, kind, equipment_type_id, equipment_id, defect_id, work_order_id, problem, root_cause, solution, preventive_actions, tags, author_id, view_count)
SELECT ('00000000-0000-0000-0000-' || '0000000e' || lpad(gs::text, 4, '0'))::uuid, now(), now(), false,
       'NAV-KB-' || (ARRAY['CMP','PMP','RCT','HEX','VALVE','MOTOR','INST','PPR'])[1 + ((gs - 1) % 8)] || '-' || lpad(gs::text, 3, '0'),
       (ARRAY['Compressor vibration troubleshooting','Pump seal replacement procedure','Reactor agitator inspection lesson','Heat exchanger cleaning procedure','Control valve calibration lesson','Motor insulation diagnostic note','Pressure transmitter drift response','Monthly PPR preparation checklist'])[1 + ((gs - 1) % 8)] || ' ' || lpad(gs::text, 3, '0'),
       (ARRAY['TROUBLESHOOTING','PROCEDURE','LESSON_LEARNED','PROCEDURE'])[1 + ((gs - 1) % 4)],
       ('00000000-0000-0000-0000-' || '00000000f00' || (1 + ((gs - 1) % 8))::text)::uuid,
       ('00000000-0000-0000-0000-' || '00000001' || lpad((1 + ((gs - 1) % 60))::text, 4, '0'))::uuid,
       CASE WHEN gs % 3 = 0 THEN ('00000000-0000-0000-0000-' || '00000009' || lpad((1 + ((gs - 1) % 30))::text, 4, '0'))::uuid ELSE NULL END,
       CASE WHEN gs % 4 = 0 THEN ('00000000-0000-0000-0000-' || '00000008' || lpad((1 + ((gs - 1) % 50))::text, 4, '0'))::uuid ELSE NULL END,
       'Observed maintenance issue from industrial production-like operating history',
       'Root cause documented from inspection, repair request, or work order analysis',
       'Follow approved maintenance procedure and record verification results',
       'Add trend review, spare readiness check, and operator walkdown item',
       jsonb_build_array('industrial', 'navoiyazot', lower((ARRAY['compressor','pump','reactor','heat-exchanger','valve','motor','instrument','ppr'])[1 + ((gs - 1) % 8)])),
       '00000000-0000-0000-0000-00000000a009'::uuid,
       5 + (gs * 3)
FROM generate_series(3, 18) AS gs
ON CONFLICT (code) DO NOTHING;

-- 17.04.X Finance/docs/audit supplements.
INSERT INTO financial_approval_rules (id, created_at, updated_at, is_deleted, code, name, required_role_code, escalate_to_role_code, min_amount, max_amount, threshold_hours, priority, department_id, notes, is_active)
SELECT ('10000000-0000-0000-0000-' || '00000041' || lpad(gs::text, 4, '0'))::uuid,
       now(), now(), false,
       'FIN-APR-' || lpad(gs::text, 3, '0'),
       'Finance approval rule ' || lpad(gs::text, 3, '0'),
       (ARRAY['ECONOMIST','CHIEF_MECHANIC','TECHNICAL_DIRECTOR'])[1 + ((gs - 1) % 3)],
       (ARRAY['CHIEF_MECHANIC','TECHNICAL_DIRECTOR','SYSTEM_ADMIN'])[1 + ((gs - 1) % 3)],
       100000 * gs,
       100000 * gs + 1500000,
       4 + (gs % 18),
       gs,
       ('00000000-0000-0000-0000-' || '00000000d00' || (1 + ((gs - 1) % 5))::text)::uuid,
       'Rule for maintenance budget/procurement/actual cost approvals',
       true
FROM generate_series(1, 15) AS gs
ON CONFLICT (code) DO NOTHING;

INSERT INTO contractors (id, created_at, updated_at, is_deleted, code, name, specialization, contact_person, phone, email, tax_number, status)
SELECT ('10000000-0000-0000-0000-' || '00000042' || lpad(gs::text, 4, '0'))::uuid,
       now(), now(), false,
       'CTR-NAV-' || lpad(gs::text, 3, '0'),
       (ARRAY['Navoiy Service Group','PromMontaj Alliance','UzIndustrial Repair','KIP Service Team','Turbo Dynamics'])[1 + ((gs - 1) % 5)] || ' #' || lpad(gs::text, 3, '0'),
       (ARRAY['Rotating equipment','Welding and fabrication','Instrumentation','Electrical','General maintenance'])[1 + ((gs - 1) % 5)],
       'Navoiyazot contact ' || gs,
       '+998911110' || lpad(gs::text, 3, '0'),
       'contractor' || gs || '@nav.local',
       'TIN-NAV-' || lpad(gs::text, 6, '0'),
       (ARRAY['ACTIVE','ACTIVE','ACTIVE','INACTIVE'])[1 + ((gs - 1) % 4)]
FROM generate_series(1, 15) AS gs
ON CONFLICT (code) DO NOTHING;

INSERT INTO contractor_contracts (id, created_at, updated_at, is_deleted, contractor_id, number, subject, status, amount, start_date, end_date)
SELECT ('10000000-0000-0000-0000-' || '00000043' || lpad(gs::text, 4, '0'))::uuid,
       now(), now(), false,
       ('10000000-0000-0000-0000-' || '00000042' || lpad((1 + ((gs - 1) % 15))::text, 4, '0'))::uuid,
       'CNTR-2026-' || lpad(gs::text, 4, '0'),
       (ARRAY['Overhaul support','Emergency repair standby','Inspection services','Calibration support'])[1 + ((gs - 1) % 4)],
       (ARRAY['ACTIVE','ACTIVE','DRAFT','EXPIRED'])[1 + ((gs - 1) % 4)],
       8000000 + (gs * 450000),
       DATE '2026-01-01' + (gs * 7),
       DATE '2026-12-31'
FROM generate_series(1, 15) AS gs
ON CONFLICT (number) DO NOTHING;

INSERT INTO contractor_works (id, created_at, updated_at, is_deleted, contractor_id, work_order_id, created_by_id, accepted_by_id, description, status, cost, started_at, completed_at, accepted_at, result, acceptance_comment)
SELECT ('10000000-0000-0000-0000-' || '00000044' || lpad(gs::text, 4, '0'))::uuid,
       now(), now(), false,
       ('10000000-0000-0000-0000-' || '00000042' || lpad((1 + ((gs - 1) % 15))::text, 4, '0'))::uuid,
       ('00000000-0000-0000-0000-' || '00000008' || lpad((1 + ((gs - 1) % 50))::text, 4, '0'))::uuid,
       '00000000-0000-0000-0000-00000000a003'::uuid,
       CASE WHEN gs % 4 IN (2, 3) THEN '00000000-0000-0000-0000-00000000a002'::uuid ELSE NULL END,
       'Contractor execution for industrial maintenance work order ' || gs,
       (ARRAY['DRAFT','IN_PROGRESS','COMPLETED','ACCEPTED'])[1 + ((gs - 1) % 4)],
       650000 + (gs * 120000),
       now() - ((gs % 20) || ' days')::interval,
       CASE WHEN gs % 4 IN (3, 0) THEN now() - ((gs % 10) || ' days')::interval ELSE NULL END,
       CASE WHEN gs % 4 = 0 THEN now() - ((gs % 8) || ' days')::interval ELSE NULL END,
       CASE WHEN gs % 4 = 0 THEN 'Accepted by workshop commission' ELSE NULL END,
       CASE WHEN gs % 4 = 0 THEN 'Quality accepted' ELSE NULL END
FROM generate_series(1, 20) AS gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO actual_cost_review_route_overrides (id, created_at, updated_at, is_deleted, actual_cost_id, department_id, approval_role_code, escalation_role_code, threshold_hours, comment, is_active)
SELECT ('10000000-0000-0000-0000-' || '00000045' || lpad(gs::text, 4, '0'))::uuid,
       now(), now(), false,
       ('00000000-0000-0000-0000-' || '0000000b' || lpad((200 + (1 + ((gs - 1) % 30)))::text, 4, '0'))::uuid,
       ('00000000-0000-0000-0000-' || '00000000d00' || (1 + ((gs - 1) % 5))::text)::uuid,
       (ARRAY['ECONOMIST','CHIEF_MECHANIC','TECHNICAL_DIRECTOR'])[1 + ((gs - 1) % 3)],
       (ARRAY['CHIEF_MECHANIC','TECHNICAL_DIRECTOR','SYSTEM_ADMIN'])[1 + ((gs - 1) % 3)],
       8 + (gs % 24),
       'Override approval route for high-value or urgent actual cost',
       true
FROM generate_series(1, 10) AS gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO uploaded_files (id, original_name, stored_name, object_name, url, content_type, extension, size_bytes, uploaded_by, category, deleted, created_at, deleted_at)
SELECT ('10000000-0000-0000-0000-' || '00000046' || lpad(gs::text, 4, '0'))::uuid,
       'NAV-file-' || lpad(gs::text, 3, '0') || '.pdf',
       'stored-NAV-file-' || lpad(gs::text, 3, '0') || '.pdf',
       'industrial/object/' || lpad(gs::text, 3, '0') || '.pdf',
       'https://files.nav.local/object/' || lpad(gs::text, 3, '0') || '.pdf',
       'application/pdf',
       'pdf',
       120000 + (gs * 900),
       '00000000-0000-0000-0000-00000000a003'::uuid,
       (ARRAY['PASSPORT','PERMIT','ACT','MANUAL'])[1 + ((gs - 1) % 4)],
       false,
       now() - ((gs % 12) || ' days')::interval,
       NULL
FROM generate_series(1, 30) AS gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO file_assets (id, created_at, is_deleted, original_name, file_name, storage_path, mime_type, size_bytes, entity_type, entity_id, uploaded_by_id)
SELECT ('10000000-0000-0000-0000-' || '00000047' || lpad(gs::text, 4, '0'))::uuid,
       now(), false,
       'legacy-file-' || lpad(gs::text, 3, '0') || '.pdf',
       'legacy-store-' || lpad(gs::text, 3, '0') || '.pdf',
       '/uploads/nav/legacy-store-' || lpad(gs::text, 3, '0') || '.pdf',
       'application/pdf',
       98000 + (gs * 700),
       (ARRAY['TECHNICAL_DOCUMENT','ACTUAL_COST','SAFETY_PERMIT'])[1 + ((gs - 1) % 3)],
       (ARRAY['NAV-TD-001','NAV-AC-001','NAV-SP-001'])[1 + ((gs - 1) % 3)],
       '00000000-0000-0000-0000-00000000a003'::uuid
FROM generate_series(1, 20) AS gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO technical_documents (id, created_at, updated_at, is_deleted, equipment_id, equipment_node_id, file_id, uploaded_by_id, title, type, revision, document_date)
SELECT ('10000000-0000-0000-0000-' || '00000048' || lpad(gs::text, 4, '0'))::uuid,
       now(), now(), false,
       ('00000000-0000-0000-0000-' || '00000001' || lpad((1 + ((gs - 1) % 60))::text, 4, '0'))::uuid,
       ('10000000-0000-0000-0000-' || '00000013' || lpad((1 + ((gs - 1) % 60))::text, 4, '0'))::uuid,
       ('10000000-0000-0000-0000-' || '00000046' || lpad((1 + ((gs - 1) % 30))::text, 4, '0'))::uuid,
       '00000000-0000-0000-0000-00000000a003'::uuid,
       (ARRAY['Equipment passport','Maintenance instruction','Safety permit attachment','Inspection report'])[1 + ((gs - 1) % 4)] || ' #' || lpad(gs::text, 3, '0'),
       (ARRAY['PASSPORT','MANUAL','ACT','REPORT'])[1 + ((gs - 1) % 4)],
       'R' || (1 + (gs % 3)),
       DATE '2026-01-01' + (gs % 120)
FROM generate_series(1, 24) AS gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO notifications (id, created_at, updated_at, is_deleted, recipient_id, channel, title, message, severity, status, entity_type, entity_id, read_at)
SELECT ('10000000-0000-0000-0000-' || '00000049' || lpad(gs::text, 4, '0'))::uuid,
       now(), now(), false,
       ('00000000-0000-0000-0000-' || '00000000a0' || lpad((1 + ((gs - 1) % 12))::text, 2, '0'))::uuid,
       (ARRAY['WEB','EMAIL'])[1 + ((gs - 1) % 2)],
       (ARRAY['Low stock alert','Work order overdue','PPR task overdue','Approval pending'])[1 + ((gs - 1) % 4)] || ' #' || lpad(gs::text, 3, '0'),
       'Navoiyazot notification for maintenance workflow traceability',
       (ARRAY['INFO','WARNING','CRITICAL'])[1 + ((gs - 1) % 3)],
       (ARRAY['PENDING','SENT','READ'])[1 + ((gs - 1) % 3)],
       (ARRAY['WAREHOUSE_STOCK','WORK_ORDER','PPR_TASK','APPROVAL_REQUEST'])[1 + ((gs - 1) % 4)],
       (ARRAY['NAV-WH-MAIN','WO-2026-001','NAV-PPR-TASK-001','NAV-PR-2026-0001'])[1 + ((gs - 1) % 4)],
       CASE WHEN gs % 3 = 0 THEN now() - ((gs % 5) || ' hours')::interval ELSE NULL END
FROM generate_series(1, 32) AS gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO audit_logs (id, created_at, is_deleted, user_id, module, action, entity_type, entity_id, message, ip_address, user_agent)
SELECT ('10000000-0000-0000-0000-' || '0000004a' || lpad(gs::text, 4, '0'))::uuid,
       now() - ((gs % 14) || ' hours')::interval,
       false,
       ('00000000-0000-0000-0000-' || '00000000a0' || lpad((1 + ((gs - 1) % 12))::text, 2, '0'))::uuid,
       (ARRAY['WORK_ORDER','REPAIR_REQUEST','PPR_TASK','APPROVAL_REQUEST','WAREHOUSE'])[1 + ((gs - 1) % 5)],
       (ARRAY['CREATE','UPDATE','APPROVE','CLOSE'])[1 + ((gs - 1) % 4)],
       (ARRAY['WORK_ORDER','REPAIR_REQUEST','PPR_TASK','APPROVAL_REQUEST','WAREHOUSE'])[1 + ((gs - 1) % 5)],
       (ARRAY['WO-2026-001','RR-2026-001','NAV-PPR-TASK-001','NAV-PR-2026-0001','NAV-WH-MAIN'])[1 + ((gs - 1) % 5)],
       'Navoiyazot audit trail event',
       '10.10.10.' || (10 + gs),
       'SeedRun/1.0'
FROM generate_series(1, 32) AS gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO integration_endpoints (id, created_at, updated_at, is_deleted, code, name, system, url, base_path, auth_type, username, password, api_key, port, timeout_seconds, sync_interval_minutes, sync_scada, sync_production, sync_work_orders, sync_defects, sync_downtimes, is_active, last_sync_status, last_sync_at, last_error)
SELECT ('10000000-0000-0000-0000-' || '0000004b' || lpad(gs::text, 4, '0'))::uuid,
       now(), now(), false,
       (ARRAY['INT-SCADA','INT-ERP','INT-MES','INT-LIMS'])[gs],
       (ARRAY['SCADA connector','ERP connector','MES connector','LIMS connector'])[gs],
       (ARRAY['SCADA','ERP','MES','LIMS'])[gs],
       'https://integration-' || lower((ARRAY['scada','erp','mes','lims'])[gs]) || '.nav.local',
       '/api/v1',
       'BASIC',
       'NAV-user',
       'NAV-pass',
       NULL,
       443,
       30,
       15,
       CASE WHEN gs = 1 THEN true ELSE false END,
       CASE WHEN gs IN (2,3) THEN true ELSE false END,
       CASE WHEN gs = 2 THEN true ELSE false END,
       CASE WHEN gs = 4 THEN true ELSE false END,
       CASE WHEN gs = 3 THEN true ELSE false END,
       true,
       (ARRAY['SUCCESS','FAILED','RUNNING','SUCCESS'])[gs],
       now() - ((gs * 2) || ' hours')::interval,
       CASE WHEN gs = 2 THEN 'Temporary auth failure resolved' ELSE NULL END
FROM generate_series(1, 4) AS gs
ON CONFLICT (code) DO NOTHING;

INSERT INTO webhook_subscriptions (id, created_at, updated_at, is_deleted, code, name, target_url, secret, events, is_active, failure_count, last_delivery_at, last_delivery_status)
SELECT ('10000000-0000-0000-0000-' || '0000004c' || lpad(gs::text, 4, '0'))::uuid,
       now(), now(), false,
       'WH-SUB-' || lpad(gs::text, 3, '0'),
       'Webhook subscription #' || gs,
       'https://webhook-' || gs || '.nav.local/events',
       'secret-' || gs,
       jsonb_build_array('WORK_ORDER_CREATED','WORK_ORDER_UPDATED','REPAIR_REQUEST_CREATED'),
       CASE WHEN gs % 4 = 0 THEN false ELSE true END,
       gs % 3,
       now() - ((gs % 8) || ' hours')::interval,
       CASE WHEN gs % 3 = 0 THEN 'FAILED' ELSE 'SUCCESS' END
FROM generate_series(1, 8) AS gs
ON CONFLICT (code) DO NOTHING;

INSERT INTO integration_sync_logs (id, created_at, updated_at, is_deleted, endpoint_id, module, direction, status, started_at, finished_at, records_sent, records_received, error_message)
SELECT ('10000000-0000-0000-0000-' || '0000004d' || lpad(gs::text, 4, '0'))::uuid,
       now(), now(), false,
       ('10000000-0000-0000-0000-' || '0000004b' || lpad((1 + ((gs - 1) % 4))::text, 4, '0'))::uuid,
       (ARRAY['WORK_ORDERS','DEFECTS','PRODUCTION','SCADA'])[1 + ((gs - 1) % 4)],
       (ARRAY['OUTBOUND','INBOUND'])[1 + ((gs - 1) % 2)],
       (ARRAY['SUCCESS','FAILED','RUNNING'])[1 + ((gs - 1) % 3)],
       now() - ((gs % 10) || ' hours')::interval,
       CASE WHEN gs % 3 <> 0 THEN now() - ((gs % 10) || ' hours')::interval + interval '5 minutes' ELSE NULL END,
       20 + (gs * 3),
       15 + (gs * 2),
       CASE WHEN gs % 3 = 2 THEN 'Timeout on upstream source' ELSE NULL END
FROM generate_series(1, 6) AS gs
ON CONFLICT (id) DO NOTHING;

INSERT INTO webhook_event_log (id, created_at, updated_at, is_deleted, subscription_id, event_code, fired_at, http_status, payload, error)
SELECT ('10000000-0000-0000-0000-' || '0000004e' || lpad(gs::text, 4, '0'))::uuid,
       now(), now(), false,
       ('10000000-0000-0000-0000-' || '0000004c' || lpad((1 + ((gs - 1) % 8))::text, 4, '0'))::uuid,
       (ARRAY['WORK_ORDER_CREATED','WORK_ORDER_UPDATED','REPAIR_REQUEST_CREATED'])[1 + ((gs - 1) % 3)],
       now() - ((gs % 12) || ' hours')::interval,
       CASE WHEN gs % 4 = 0 THEN 500 ELSE 200 END,
       '{"seeded":true}'::text,
       CASE WHEN gs % 4 = 0 THEN 'Remote endpoint returned 500' ELSE NULL END
FROM generate_series(1, 8) AS gs
ON CONFLICT (id) DO NOTHING;
