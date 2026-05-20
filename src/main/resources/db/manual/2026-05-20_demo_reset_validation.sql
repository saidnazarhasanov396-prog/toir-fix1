-- TOIR demo reset validation.
-- SELECT-only. Run after approved delete/seed scripts on the target copy.

SELECT
    current_database() AS database_name,
    current_user AS database_user,
    inet_server_addr() AS server_address,
    inet_server_port() AS server_port,
    now() AS checked_at;

SELECT
    CASE WHEN count(*) = 1 THEN 'OK' ELSE 'FAIL' END AS system_admin_wildcard_status,
    count(*) AS matching_roles
FROM roles
WHERE code = 'SYSTEM_ADMIN'
  AND is_deleted = false
  AND permissions @> '["*"]'::jsonb;

SELECT
    CASE WHEN count(*) >= 1 THEN 'OK' ELSE 'FAIL' END AS admin_user_status,
    count(*) AS active_admin_users
FROM users u
WHERE u.is_deleted = false
  AND (
      u.username IN ('admin', 'system')
      OR EXISTS (
          SELECT 1
          FROM user_roles ur
          JOIN roles r ON r.id = ur.role_id
          WHERE ur.user_id = u.id
            AND r.code = 'SYSTEM_ADMIN'
            AND r.is_deleted = false
      )
      OR EXISTS (
          SELECT 1 FROM roles pr WHERE pr.id = u.primary_role_id AND pr.code = 'SYSTEM_ADMIN'
      )
  );

SELECT code, jsonb_array_length(COALESCE(permissions, '[]'::jsonb)) AS permission_count
FROM roles
WHERE is_deleted = false
ORDER BY code;

SELECT 'departments' AS check_name, count(*) AS demo_count FROM departments WHERE code LIKE 'DEMO-%' AND is_deleted = false
UNION ALL SELECT 'demo_users', count(*) FROM users WHERE username LIKE 'demo_%' AND is_deleted = false
UNION ALL SELECT 'employees', count(*) FROM hr_employees WHERE personnel_number LIKE 'DEMO-%' AND is_deleted = false
UNION ALL SELECT 'locations', count(*) FROM locations WHERE code LIKE 'DEMO-%' AND is_deleted = false
UNION ALL SELECT 'equipment_types', count(*) FROM equipment_types WHERE code LIKE 'DEMO-%' AND is_deleted = false
UNION ALL SELECT 'equipment', count(*) FROM equipment WHERE code LIKE 'DEMO-%' AND is_deleted = false
UNION ALL SELECT 'warehouses', count(*) FROM warehouses WHERE code LIKE 'DEMO-%' AND is_deleted = false
UNION ALL SELECT 'spare_parts', count(*) FROM spare_parts WHERE code LIKE 'DEMO-%' AND is_deleted = false
UNION ALL SELECT 'warehouse_stocks', count(*) FROM warehouse_stocks ws JOIN warehouses w ON w.id = ws.warehouse_id WHERE w.code LIKE 'DEMO-%' AND ws.is_deleted = false
UNION ALL SELECT 'stock_movements', count(*) FROM stock_movements WHERE document_number LIKE 'DEMO-%' AND is_deleted = false
UNION ALL SELECT 'ppr_tasks', count(*) FROM ppr_tasks WHERE code LIKE 'DEMO-%' AND is_deleted = false
UNION ALL SELECT 'repair_requests', count(*) FROM repair_requests WHERE number LIKE 'DEMO-%' AND is_deleted = false
UNION ALL SELECT 'work_orders', count(*) FROM work_orders WHERE number LIKE 'DEMO-%' AND is_deleted = false
UNION ALL SELECT 'defects', count(*) FROM defects WHERE code LIKE 'DEMO-%' AND is_deleted = false
UNION ALL SELECT 'defect_lists', count(*) FROM defect_lists WHERE code LIKE 'DEMO-%' AND is_deleted = false
UNION ALL SELECT 'defect_list_lines', count(*) FROM defect_list_lines WHERE id::text LIKE '00000000-0000-0000-0000-00000009%' AND is_deleted = false
UNION ALL SELECT 'procurement_requests', count(*) FROM procurement_requests WHERE number LIKE 'DEMO-%' AND is_deleted = false
UNION ALL SELECT 'procurement_request_lines', count(*) FROM procurement_request_lines WHERE id::text LIKE '00000000-0000-0000-0000-0000000a%' AND is_deleted = false
UNION ALL SELECT 'maintenance_budgets', count(*) FROM maintenance_budgets WHERE id::text LIKE '00000000-0000-0000-0000-0000000b%' AND is_deleted = false
UNION ALL SELECT 'budget_lines', count(*) FROM budget_lines WHERE id::text LIKE '00000000-0000-0000-0000-0000000b%' AND is_deleted = false
UNION ALL SELECT 'actual_costs', count(*) FROM actual_costs WHERE id::text LIKE '00000000-0000-0000-0000-0000000b%' AND is_deleted = false
UNION ALL SELECT 'approvals', count(*) FROM approval_requests WHERE id::text LIKE '00000000-0000-0000-0000-0000000c%' AND is_deleted = false
UNION ALL SELECT 'approval_steps', count(*) FROM approval_steps WHERE id::text LIKE '00000000-0000-0000-0000-0000000c%' AND is_deleted = false
UNION ALL SELECT 'inspection_routes', count(*) FROM inspection_routes WHERE code LIKE 'DEMO-%' AND is_deleted = false
UNION ALL SELECT 'inspection_checkpoints', count(*) FROM inspection_checkpoints WHERE id::text LIKE '00000000-0000-0000-0000-0000000d%' AND is_deleted = false
UNION ALL SELECT 'inspection_rounds', count(*) FROM inspection_rounds WHERE id::text LIKE '00000000-0000-0000-0000-0000000d%' AND is_deleted = false
UNION ALL SELECT 'inspection_results', count(*) FROM inspection_round_results WHERE id::text LIKE '00000000-0000-0000-0000-0000000d%' AND is_deleted = false
UNION ALL SELECT 'knowledge_articles', count(*) FROM knowledge_articles WHERE code LIKE 'DEMO-%' AND is_deleted = false
ORDER BY check_name;

WITH expected(check_name, min_count) AS (
    VALUES
        ('departments', 5),
        ('demo_users', 12),
        ('employees', 25),
        ('locations', 12),
        ('equipment_types', 8),
        ('equipment', 60),
        ('warehouses', 3),
        ('spare_parts', 60),
        ('warehouse_stocks', 150),
        ('stock_movements', 60),
        ('ppr_tasks', 75),
        ('repair_requests', 40),
        ('work_orders', 50),
        ('defects', 30),
        ('defect_lists', 10),
        ('defect_list_lines', 40),
        ('procurement_requests', 12),
        ('procurement_request_lines', 36),
        ('maintenance_budgets', 3),
        ('budget_lines', 18),
        ('actual_costs', 30),
        ('approvals', 12),
        ('approval_steps', 24),
        ('inspection_routes', 6),
        ('inspection_checkpoints', 36),
        ('inspection_rounds', 24),
        ('inspection_results', 120),
        ('knowledge_articles', 18)
),
actual(check_name, actual_count) AS (
    SELECT 'departments', count(*) FROM departments WHERE code LIKE 'DEMO-%' AND is_deleted = false
    UNION ALL SELECT 'demo_users', count(*) FROM users WHERE username LIKE 'demo_%' AND is_deleted = false
    UNION ALL SELECT 'employees', count(*) FROM hr_employees WHERE personnel_number LIKE 'DEMO-%' AND is_deleted = false
    UNION ALL SELECT 'locations', count(*) FROM locations WHERE code LIKE 'DEMO-%' AND is_deleted = false
    UNION ALL SELECT 'equipment_types', count(*) FROM equipment_types WHERE code LIKE 'DEMO-%' AND is_deleted = false
    UNION ALL SELECT 'equipment', count(*) FROM equipment WHERE code LIKE 'DEMO-%' AND is_deleted = false
    UNION ALL SELECT 'warehouses', count(*) FROM warehouses WHERE code LIKE 'DEMO-%' AND is_deleted = false
    UNION ALL SELECT 'spare_parts', count(*) FROM spare_parts WHERE code LIKE 'DEMO-%' AND is_deleted = false
    UNION ALL SELECT 'warehouse_stocks', count(*) FROM warehouse_stocks ws JOIN warehouses w ON w.id = ws.warehouse_id WHERE w.code LIKE 'DEMO-%' AND ws.is_deleted = false
    UNION ALL SELECT 'stock_movements', count(*) FROM stock_movements WHERE document_number LIKE 'DEMO-%' AND is_deleted = false
    UNION ALL SELECT 'ppr_tasks', count(*) FROM ppr_tasks WHERE code LIKE 'DEMO-%' AND is_deleted = false
    UNION ALL SELECT 'repair_requests', count(*) FROM repair_requests WHERE number LIKE 'DEMO-%' AND is_deleted = false
    UNION ALL SELECT 'work_orders', count(*) FROM work_orders WHERE number LIKE 'DEMO-%' AND is_deleted = false
    UNION ALL SELECT 'defects', count(*) FROM defects WHERE code LIKE 'DEMO-%' AND is_deleted = false
    UNION ALL SELECT 'defect_lists', count(*) FROM defect_lists WHERE code LIKE 'DEMO-%' AND is_deleted = false
    UNION ALL SELECT 'defect_list_lines', count(*) FROM defect_list_lines WHERE id::text LIKE '00000000-0000-0000-0000-00000009%' AND is_deleted = false
    UNION ALL SELECT 'procurement_requests', count(*) FROM procurement_requests WHERE number LIKE 'DEMO-%' AND is_deleted = false
    UNION ALL SELECT 'procurement_request_lines', count(*) FROM procurement_request_lines WHERE id::text LIKE '00000000-0000-0000-0000-0000000a%' AND is_deleted = false
    UNION ALL SELECT 'maintenance_budgets', count(*) FROM maintenance_budgets WHERE id::text LIKE '00000000-0000-0000-0000-0000000b%' AND is_deleted = false
    UNION ALL SELECT 'budget_lines', count(*) FROM budget_lines WHERE id::text LIKE '00000000-0000-0000-0000-0000000b%' AND is_deleted = false
    UNION ALL SELECT 'actual_costs', count(*) FROM actual_costs WHERE id::text LIKE '00000000-0000-0000-0000-0000000b%' AND is_deleted = false
    UNION ALL SELECT 'approvals', count(*) FROM approval_requests WHERE id::text LIKE '00000000-0000-0000-0000-0000000c%' AND is_deleted = false
    UNION ALL SELECT 'approval_steps', count(*) FROM approval_steps WHERE id::text LIKE '00000000-0000-0000-0000-0000000c%' AND is_deleted = false
    UNION ALL SELECT 'inspection_routes', count(*) FROM inspection_routes WHERE code LIKE 'DEMO-%' AND is_deleted = false
    UNION ALL SELECT 'inspection_checkpoints', count(*) FROM inspection_checkpoints WHERE id::text LIKE '00000000-0000-0000-0000-0000000d%' AND is_deleted = false
    UNION ALL SELECT 'inspection_rounds', count(*) FROM inspection_rounds WHERE id::text LIKE '00000000-0000-0000-0000-0000000d%' AND is_deleted = false
    UNION ALL SELECT 'inspection_results', count(*) FROM inspection_round_results WHERE id::text LIKE '00000000-0000-0000-0000-0000000d%' AND is_deleted = false
    UNION ALL SELECT 'knowledge_articles', count(*) FROM knowledge_articles WHERE code LIKE 'DEMO-%' AND is_deleted = false
)
SELECT e.check_name, e.min_count, a.actual_count,
       CASE WHEN a.actual_count >= e.min_count THEN 'OK' ELSE 'FAIL' END AS minimum_status
FROM expected e
JOIN actual a ON a.check_name = e.check_name
ORDER BY e.check_name;

SELECT 'equipment.department_id' AS relation_name, count(*) AS orphan_count
FROM equipment e
LEFT JOIN departments d ON d.id = e.department_id
WHERE e.code LIKE 'DEMO-%' AND e.department_id IS NOT NULL AND d.id IS NULL
UNION ALL SELECT 'equipment.equipment_type_id', count(*)
FROM equipment e
LEFT JOIN equipment_types et ON et.id = e.equipment_type_id
WHERE e.code LIKE 'DEMO-%' AND et.id IS NULL
UNION ALL SELECT 'warehouse_stocks.warehouse_id', count(*)
FROM warehouse_stocks ws
JOIN warehouses w0 ON w0.id = ws.warehouse_id AND w0.code LIKE 'DEMO-%'
LEFT JOIN warehouses w ON w.id = ws.warehouse_id
WHERE w.id IS NULL
UNION ALL SELECT 'warehouse_stocks.spare_part_id', count(*)
FROM warehouse_stocks ws
JOIN warehouses w0 ON w0.id = ws.warehouse_id AND w0.code LIKE 'DEMO-%'
LEFT JOIN spare_parts sp ON sp.id = ws.spare_part_id
WHERE sp.id IS NULL
UNION ALL SELECT 'stock_movements.warehouse_id', count(*)
FROM stock_movements sm
LEFT JOIN warehouses w ON w.id = sm.warehouse_id
WHERE sm.document_number LIKE 'DEMO-%' AND sm.warehouse_id IS NOT NULL AND w.id IS NULL
UNION ALL SELECT 'stock_movements.spare_part_id', count(*)
FROM stock_movements sm
LEFT JOIN spare_parts sp ON sp.id = sm.spare_part_id
WHERE sm.document_number LIKE 'DEMO-%' AND sm.spare_part_id IS NOT NULL AND sp.id IS NULL
UNION ALL SELECT 'stock_movements.work_order_id', count(*)
FROM stock_movements sm
LEFT JOIN work_orders wo ON wo.id = sm.work_order_id
WHERE sm.document_number LIKE 'DEMO-%' AND sm.work_order_id IS NOT NULL AND wo.id IS NULL
UNION ALL SELECT 'ppr_tasks.plan_id', count(*)
FROM ppr_tasks t
LEFT JOIN ppr_plans p ON p.id = t.plan_id
WHERE t.code LIKE 'DEMO-%' AND p.id IS NULL
UNION ALL SELECT 'ppr_tasks.equipment_id', count(*)
FROM ppr_tasks t
LEFT JOIN equipment e ON e.id = t.equipment_id
WHERE t.code LIKE 'DEMO-%' AND e.id IS NULL
UNION ALL SELECT 'repair_requests.equipment_id', count(*)
FROM repair_requests rr
LEFT JOIN equipment e ON e.id = rr.equipment_id
WHERE rr.number LIKE 'DEMO-%' AND e.id IS NULL
UNION ALL SELECT 'work_orders.equipment_id', count(*)
FROM work_orders wo
LEFT JOIN equipment e ON e.id = wo.equipment_id
WHERE wo.number LIKE 'DEMO-%' AND e.id IS NULL
UNION ALL SELECT 'work_order_tasks.work_order_id', count(*)
FROM work_order_tasks wot
LEFT JOIN work_orders wo ON wo.id = wot.work_order_id
WHERE wot.id::text LIKE '00000000-0000-0000-0000-00000008%' AND wo.id IS NULL
UNION ALL SELECT 'repair_material_usages.work_order_id', count(*)
FROM repair_material_usages rmu
LEFT JOIN work_orders wo ON wo.id = rmu.work_order_id
WHERE rmu.id::text LIKE '00000000-0000-0000-0000-000000083%' AND wo.id IS NULL
UNION ALL SELECT 'repair_material_usages.warehouse_id', count(*)
FROM repair_material_usages rmu
LEFT JOIN warehouses w ON w.id = rmu.warehouse_id
WHERE rmu.id::text LIKE '00000000-0000-0000-0000-000000083%' AND w.id IS NULL
UNION ALL SELECT 'repair_material_usages.spare_part_id', count(*)
FROM repair_material_usages rmu
LEFT JOIN spare_parts sp ON sp.id = rmu.spare_part_id
WHERE rmu.id::text LIKE '00000000-0000-0000-0000-000000083%' AND sp.id IS NULL
UNION ALL SELECT 'labor_entries.work_order_id', count(*)
FROM labor_entries le
LEFT JOIN work_orders wo ON wo.id = le.work_order_id
WHERE le.id::text LIKE '00000000-0000-0000-0000-000000084%' AND wo.id IS NULL
UNION ALL SELECT 'work_executions.work_order_id', count(*)
FROM work_executions we
LEFT JOIN work_orders wo ON wo.id = we.work_order_id
WHERE we.id::text LIKE '00000000-0000-0000-0000-000000085%' AND wo.id IS NULL
UNION ALL SELECT 'defects.equipment_id', count(*)
FROM defects d
LEFT JOIN equipment e ON e.id = d.equipment_id
WHERE d.code LIKE 'DEMO-%' AND e.id IS NULL
UNION ALL SELECT 'defects.repair_request_id', count(*)
FROM defects d
LEFT JOIN repair_requests rr ON rr.id = d.repair_request_id
WHERE d.code LIKE 'DEMO-%' AND d.repair_request_id IS NOT NULL AND rr.id IS NULL
UNION ALL SELECT 'defect_lists.equipment_id', count(*)
FROM defect_lists dl
LEFT JOIN equipment e ON e.id = dl.equipment_id
WHERE dl.code LIKE 'DEMO-%' AND dl.equipment_id IS NOT NULL AND e.id IS NULL
UNION ALL SELECT 'defect_lists.repair_request_id', count(*)
FROM defect_lists dl
LEFT JOIN repair_requests rr ON rr.id = dl.repair_request_id
WHERE dl.code LIKE 'DEMO-%' AND dl.repair_request_id IS NOT NULL AND rr.id IS NULL
UNION ALL SELECT 'defect_lists.work_order_id', count(*)
FROM defect_lists dl
LEFT JOIN work_orders wo ON wo.id = dl.work_order_id
WHERE dl.code LIKE 'DEMO-%' AND dl.work_order_id IS NOT NULL AND wo.id IS NULL
UNION ALL SELECT 'defect_list_lines.defect_list_id', count(*)
FROM defect_list_lines dll
LEFT JOIN defect_lists dl ON dl.id = dll.defect_list_id
WHERE dll.id::text LIKE '00000000-0000-0000-0000-00000009%' AND dl.id IS NULL
UNION ALL SELECT 'defect_list_lines.defect_id', count(*)
FROM defect_list_lines dll
LEFT JOIN defects d ON d.id = dll.defect_id
WHERE dll.id::text LIKE '00000000-0000-0000-0000-00000009%' AND dll.defect_id IS NOT NULL AND d.id IS NULL
UNION ALL SELECT 'defect_list_lines.spare_part_id', count(*)
FROM defect_list_lines dll
LEFT JOIN spare_parts sp ON sp.id = dll.spare_part_id
WHERE dll.id::text LIKE '00000000-0000-0000-0000-00000009%' AND dll.spare_part_id IS NOT NULL AND sp.id IS NULL
UNION ALL SELECT 'procurement_request_lines.request_id', count(*)
FROM procurement_request_lines prl
LEFT JOIN procurement_requests pr ON pr.id = prl.request_id
WHERE prl.id::text LIKE '00000000-0000-0000-0000-0000000a%' AND pr.id IS NULL
UNION ALL SELECT 'procurement_request_lines.spare_part_id', count(*)
FROM procurement_request_lines prl
LEFT JOIN spare_parts sp ON sp.id = prl.spare_part_id
WHERE prl.id::text LIKE '00000000-0000-0000-0000-0000000a%' AND prl.spare_part_id IS NOT NULL AND sp.id IS NULL
UNION ALL SELECT 'maintenance_budgets.department_id', count(*)
FROM maintenance_budgets mb
LEFT JOIN departments d ON d.id = mb.department_id
WHERE mb.id::text LIKE '00000000-0000-0000-0000-0000000b%' AND mb.department_id IS NOT NULL AND d.id IS NULL
UNION ALL SELECT 'budget_lines.budget_id', count(*)
FROM budget_lines bl
LEFT JOIN maintenance_budgets mb ON mb.id = bl.budget_id
WHERE bl.id::text LIKE '00000000-0000-0000-0000-0000000b%' AND mb.id IS NULL
UNION ALL SELECT 'budget_lines.cost_category_id', count(*)
FROM budget_lines bl
LEFT JOIN cost_categories cc ON cc.id = bl.cost_category_id
WHERE bl.id::text LIKE '00000000-0000-0000-0000-0000000b%' AND bl.cost_category_id IS NOT NULL AND cc.id IS NULL
UNION ALL SELECT 'actual_costs.work_order_id', count(*)
FROM actual_costs ac
LEFT JOIN work_orders wo ON wo.id = ac.work_order_id
WHERE ac.id::text LIKE '00000000-0000-0000-0000-0000000b%' AND ac.work_order_id IS NOT NULL AND wo.id IS NULL
UNION ALL SELECT 'actual_costs.repair_request_id', count(*)
FROM actual_costs ac
LEFT JOIN repair_requests rr ON rr.id = ac.repair_request_id
WHERE ac.id::text LIKE '00000000-0000-0000-0000-0000000b%' AND ac.repair_request_id IS NOT NULL AND rr.id IS NULL
UNION ALL SELECT 'actual_costs.budget_line_id', count(*)
FROM actual_costs ac
LEFT JOIN budget_lines bl ON bl.id = ac.budget_line_id
WHERE ac.id::text LIKE '00000000-0000-0000-0000-0000000b%' AND ac.budget_line_id IS NOT NULL AND bl.id IS NULL
UNION ALL SELECT 'actual_costs.cost_category_id', count(*)
FROM actual_costs ac
LEFT JOIN cost_categories cc ON cc.id = ac.cost_category_id
WHERE ac.id::text LIKE '00000000-0000-0000-0000-0000000b%' AND ac.cost_category_id IS NOT NULL AND cc.id IS NULL
UNION ALL SELECT 'approval_steps.request_id', count(*)
FROM approval_steps aps
LEFT JOIN approval_requests ar ON ar.id = aps.request_id
WHERE aps.id::text LIKE '00000000-0000-0000-0000-0000000c%' AND ar.id IS NULL
UNION ALL SELECT 'inspection_checkpoints.route_id', count(*)
FROM inspection_checkpoints ic
LEFT JOIN inspection_routes ir ON ir.id = ic.route_id
WHERE ic.id::text LIKE '00000000-0000-0000-0000-0000000d%' AND ir.id IS NULL
UNION ALL SELECT 'inspection_round_results.round_id', count(*)
FROM inspection_round_results irr
LEFT JOIN inspection_rounds ir ON ir.id = irr.round_id
WHERE irr.id::text LIKE '00000000-0000-0000-0000-0000000d%' AND ir.id IS NULL
UNION ALL SELECT 'inspection_round_results.checkpoint_id', count(*)
FROM inspection_round_results irr
LEFT JOIN inspection_checkpoints ic ON ic.id = irr.checkpoint_id
WHERE irr.id::text LIKE '00000000-0000-0000-0000-0000000d%' AND irr.checkpoint_id IS NOT NULL AND ic.id IS NULL
UNION ALL SELECT 'inspection_round_results.defect_id', count(*)
FROM inspection_round_results irr
LEFT JOIN defects d ON d.id = irr.defect_id
WHERE irr.id::text LIKE '00000000-0000-0000-0000-0000000d%' AND irr.defect_id IS NOT NULL AND d.id IS NULL
UNION ALL SELECT 'knowledge_articles.equipment_id', count(*)
FROM knowledge_articles ka
LEFT JOIN equipment e ON e.id = ka.equipment_id
WHERE ka.code LIKE 'DEMO-%' AND ka.equipment_id IS NOT NULL AND e.id IS NULL
UNION ALL SELECT 'knowledge_articles.defect_id', count(*)
FROM knowledge_articles ka
LEFT JOIN defects d ON d.id = ka.defect_id
WHERE ka.code LIKE 'DEMO-%' AND ka.defect_id IS NOT NULL AND d.id IS NULL
UNION ALL SELECT 'knowledge_articles.work_order_id', count(*)
FROM knowledge_articles ka
LEFT JOIN work_orders wo ON wo.id = ka.work_order_id
WHERE ka.code LIKE 'DEMO-%' AND ka.work_order_id IS NOT NULL AND wo.id IS NULL
ORDER BY relation_name;

SELECT number, status, type, work_type, priority, started_at, completed_at
FROM work_orders
WHERE number LIKE 'DEMO-%'
ORDER BY number;

SELECT code, status, scheduled_start, scheduled_end, equipment_id
FROM ppr_tasks
WHERE code LIKE 'DEMO-%'
ORDER BY code;

SELECT w.code AS warehouse_code, sp.code AS spare_part_code, ws.quantity, ws.reserved_qty, ws.min_qty
FROM warehouse_stocks ws
JOIN warehouses w ON w.id = ws.warehouse_id
JOIN spare_parts sp ON sp.id = ws.spare_part_id
WHERE w.code LIKE 'DEMO-%'
ORDER BY w.code, sp.code;

SELECT number, status, total_estimated_cost
FROM procurement_requests
WHERE number LIKE 'DEMO-%'
ORDER BY number;

SELECT ac.id, ac.status, ac.amount, wo.number AS work_order_number
FROM actual_costs ac
LEFT JOIN work_orders wo ON wo.id = ac.work_order_id
WHERE ac.id::text LIKE '00000000-0000-0000-0000-0000000b%'
ORDER BY ac.id;

SELECT ar.title, ar.status, ar.current_step, count(aps.id) AS step_count
FROM approval_requests ar
LEFT JOIN approval_steps aps ON aps.request_id = ar.id
WHERE ar.id::text LIKE '00000000-0000-0000-0000-0000000c%'
GROUP BY ar.id, ar.title, ar.status, ar.current_step
ORDER BY ar.title;

SELECT route.code AS route_code, round.status AS round_status, count(result.id) AS result_count
FROM inspection_routes route
LEFT JOIN inspection_rounds round ON round.route_id = route.id
LEFT JOIN inspection_round_results result ON result.round_id = round.id
WHERE route.code LIKE 'DEMO-%'
GROUP BY route.code, round.status
ORDER BY route.code;

SELECT code, kind, title, tags
FROM knowledge_articles
WHERE code LIKE 'DEMO-%'
ORDER BY code;
