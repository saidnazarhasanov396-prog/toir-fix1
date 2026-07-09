SELECT 1 / CASE WHEN EXISTS (
    SELECT 1
    FROM departments
    WHERE code = 'NAV-AZOT'
      AND is_deleted = false
) THEN 1 ELSE 0 END AS validate_main_department_exists;

SELECT 1 / CASE WHEN (
    SELECT count(*)
    FROM departments
    WHERE code LIKE 'NAV-AZOT%'
      AND is_deleted = false
) >= 8 THEN 1 ELSE 0 END AS validate_required_departments_count;

SELECT 1 / CASE WHEN (
    SELECT count(*)
    FROM locations
    WHERE code LIKE 'NAV-LOC%'
      AND is_deleted = false
) >= 10 THEN 1 ELSE 0 END AS validate_required_locations_count;

SELECT 1 / CASE WHEN EXISTS (
    SELECT 1
    FROM warehouses
    WHERE code = 'NAV-WH-MAIN'
      AND is_deleted = false
) THEN 1 ELSE 0 END AS validate_main_warehouse_exists;

SELECT 1 / CASE WHEN (
    SELECT count(*)
    FROM hr_employees e
    JOIN departments d ON d.id = e.department_id
    WHERE d.code LIKE 'NAV-AZOT%'
      AND d.is_deleted = false
      AND e.is_deleted = false
      AND e.is_active = true
) >= 22 THEN 1 ELSE 0 END AS validate_nav_department_employee_count;

SELECT 1 / CASE WHEN (
    SELECT count(*)
    FROM brigades
    WHERE code LIKE 'NAV-%'
      AND is_deleted = false
) >= 3 THEN 1 ELSE 0 END AS validate_brigade_count;

SELECT 1 / CASE WHEN (
    SELECT count(*)
    FROM spare_parts
    WHERE code LIKE 'NAV-SP%'
      AND is_deleted = false
) >= 30 THEN 1 ELSE 0 END AS validate_spare_part_count;

SELECT 1 / CASE WHEN (
    SELECT count(*)
    FROM warehouse_stock_balances b
    JOIN warehouses w ON w.id = b.warehouse_id
    WHERE w.code = 'NAV-WH-MAIN'
      AND w.is_deleted = false
      AND b.is_deleted = false
      AND b.qty_on_hand > 0
) >= 30 THEN 1 ELSE 0 END AS validate_stock_balance_count;

SELECT 1 / CASE WHEN (
    SELECT count(*)
    FROM warehouse_stock_ledgers l
    JOIN warehouses w ON w.id = l.warehouse_id
    WHERE w.code = 'NAV-WH-MAIN'
      AND w.is_deleted = false
      AND l.is_deleted = false
) >= 30 THEN 1 ELSE 0 END AS validate_stock_ledger_count;

SELECT 1 / CASE WHEN (
    SELECT count(*)
    FROM equipment_types
    WHERE code LIKE 'NAV-ET-%'
      AND is_deleted = false
) >= 12 THEN 1 ELSE 0 END AS validate_equipment_type_count;

SELECT 1 / CASE WHEN (
    SELECT count(*)
    FROM equipment
    WHERE code LIKE 'NAV-EQ-%'
      AND category = 'PRODUCTION_EQUIPMENT'
      AND is_deleted = false
) >= 10 THEN 1 ELSE 0 END AS validate_production_equipment_count;

SELECT 1 / CASE WHEN (
    SELECT count(*)
    FROM equipment
    WHERE code LIKE 'NAV-VEH-%'
      AND category = 'VEHICLE'
      AND is_deleted = false
) >= 10 THEN 1 ELSE 0 END AS validate_vehicle_equipment_count;

SELECT 1 / CASE WHEN (
    SELECT count(*)
    FROM vehicle_details vd
    JOIN equipment e ON e.id = vd.equipment_id
    WHERE e.code LIKE 'NAV-VEH-%'
      AND e.category = 'VEHICLE'
      AND e.is_deleted = false
      AND vd.is_deleted = false
) >= 10 THEN 1 ELSE 0 END AS validate_vehicle_details_count;

SELECT 1 / CASE WHEN (
    SELECT count(*)
    FROM repair_requests
    WHERE number LIKE 'NAV-RR-2026-%'
      AND is_deleted = false
) >= 20 THEN 1 ELSE 0 END AS validate_repair_request_count;

SELECT 1 / CASE WHEN (
    SELECT count(*)
    FROM repair_requests rr
    JOIN equipment e ON e.id = rr.equipment_id
    WHERE rr.number LIKE 'NAV-RR-2026-%'
      AND e.code LIKE 'NAV-EQ-%'
      AND rr.is_deleted = false
      AND e.is_deleted = false
) >= 10 THEN 1 ELSE 0 END AS validate_production_repair_request_count;

SELECT 1 / CASE WHEN (
    SELECT count(*)
    FROM repair_requests rr
    JOIN equipment e ON e.id = rr.equipment_id
    WHERE rr.number LIKE 'NAV-RR-2026-%'
      AND e.code LIKE 'NAV-VEH-%'
      AND rr.is_deleted = false
      AND e.is_deleted = false
) >= 10 THEN 1 ELSE 0 END AS validate_vehicle_repair_request_count;

SELECT 1 / CASE WHEN (
    SELECT count(*)
    FROM work_orders
    WHERE number LIKE 'NAV-WO-2026-%'
      AND is_deleted = false
) >= 20 THEN 1 ELSE 0 END AS validate_work_order_count;

SELECT 1 / CASE WHEN (
    SELECT count(*)
    FROM defects
    WHERE code LIKE 'NAV-DEF-2026-%'
      AND is_deleted = false
) >= 10 THEN 1 ELSE 0 END AS validate_defect_count;

SELECT 1 / CASE WHEN (
    SELECT count(*)
    FROM defect_lists
    WHERE code LIKE 'NAV-DL-2026-%'
      AND is_deleted = false
) >= 10 THEN 1 ELSE 0 END AS validate_defect_list_count;

SELECT 1 / CASE WHEN (
    SELECT count(*)
    FROM repair_material_usages rmu
    JOIN work_orders wo ON wo.id = rmu.work_order_id
    WHERE wo.number LIKE 'NAV-WO-2026-%'
      AND wo.is_deleted = false
      AND rmu.is_deleted = false
) >= 18 THEN 1 ELSE 0 END AS validate_repair_material_usage_count;

SELECT 1 / CASE WHEN (
    SELECT count(*)
    FROM warehouse_stock_ledgers l
    WHERE l.reference_doc_no LIKE 'NAV-WO-2026-%'
      AND l.movement_type = 'ISSUE'
      AND l.is_deleted = false
) >= (
    SELECT count(*)
    FROM repair_material_usages rmu
    JOIN work_orders wo ON wo.id = rmu.work_order_id
    WHERE wo.number LIKE 'NAV-WO-2026-%'
      AND wo.is_deleted = false
      AND rmu.is_deleted = false
) THEN 1 ELSE 0 END AS validate_work_order_issue_ledger_count;

SELECT 1 / CASE WHEN NOT EXISTS (
    SELECT 1
    FROM warehouse_stock_balances b
    JOIN warehouses w ON w.id = b.warehouse_id
    WHERE w.code = 'NAV-WH-MAIN'
      AND w.is_deleted = false
      AND b.is_deleted = false
      AND b.qty_on_hand < 0
) THEN 1 ELSE 0 END AS validate_nav_stock_balances_nonnegative;

SELECT 1 / CASE WHEN (
    SELECT count(*)
    FROM maintenance_regulations
    WHERE code LIKE 'NAV-MR-%'
      AND is_deleted = false
      AND is_active = true
) >= 5 THEN 1 ELSE 0 END AS validate_maintenance_regulation_count;

SELECT 1 / CASE WHEN (
    SELECT count(*)
    FROM ppr_plans
    WHERE code LIKE 'NAV-PPR-2026-%'
      AND is_deleted = false
) >= 5 THEN 1 ELSE 0 END AS validate_ppr_plan_count;

SELECT 1 / CASE WHEN (
    SELECT count(*)
    FROM ppr_plan_targets ppt
    JOIN ppr_plans pp ON pp.id = ppt.plan_id
    WHERE pp.code LIKE 'NAV-PPR-2026-%'
      AND pp.is_deleted = false
      AND ppt.is_deleted = false
) >= 5 THEN 1 ELSE 0 END AS validate_ppr_plan_target_count;

SELECT 1 / CASE WHEN (
    SELECT count(*)
    FROM ppr_tasks
    WHERE code LIKE 'NAV-PPR-TASK-2026-%'
      AND is_deleted = false
) BETWEEN 25 AND 45 THEN 1 ELSE 0 END AS validate_ppr_task_count;

SELECT 1 / CASE WHEN NOT EXISTS (
    SELECT 1
    FROM ppr_tasks pt
    JOIN ppr_plans pp ON pp.id = pt.plan_id
    WHERE pp.code LIKE 'NAV-PPR-2026-%'
      AND pp.is_deleted = false
      AND pt.is_deleted = false
      AND pt.regulation_id IS NULL
      AND pt.equipment_maintenance_rule_id IS NULL
) THEN 1 ELSE 0 END AS validate_ppr_tasks_have_maintenance_source;

SELECT 1 / CASE WHEN NOT EXISTS (
    SELECT 1
    FROM ppr_tasks
    WHERE code LIKE 'NAV-PPR-TASK-2026-%'
      AND is_deleted = false
      AND scheduled_end <= scheduled_start
) THEN 1 ELSE 0 END AS validate_ppr_task_schedule_order;

SELECT 1 / CASE WHEN (
    SELECT count(*)
    FROM cost_categories
    WHERE code LIKE 'NAV-COST-%'
      AND is_deleted = false
) >= 6 THEN 1 ELSE 0 END AS validate_nav_cost_category_count;

SELECT 1 / CASE WHEN EXISTS (
    SELECT 1
    FROM maintenance_budgets mb
    JOIN departments d ON d.id = mb.department_id
    WHERE d.code = 'NAV-AZOT'
      AND mb.year = 2026
      AND mb.month IS NULL
      AND mb.status = 'APPROVED'
      AND mb.is_deleted = false
      AND d.is_deleted = false
) THEN 1 ELSE 0 END AS validate_nav_annual_budget_exists;

SELECT 1 / CASE WHEN (
    SELECT count(*)
    FROM budget_lines
    WHERE budget_id = '71000000-0000-0000-0000-000000150001'::uuid
      AND is_deleted = false
) >= 6 THEN 1 ELSE 0 END AS validate_nav_budget_line_count;

SELECT 1 / CASE WHEN (
    SELECT abs(COALESCE(sum(bl.planned_amount), 0.0) - mb.total_planned)
    FROM maintenance_budgets mb
    JOIN budget_lines bl ON bl.budget_id = mb.id
    WHERE mb.id = '71000000-0000-0000-0000-000000150001'::uuid
      AND mb.is_deleted = false
      AND bl.is_deleted = false
    GROUP BY mb.total_planned
) < 0.01 THEN 1 ELSE 0 END AS validate_nav_budget_planned_total;

SELECT 1 / CASE WHEN (
    SELECT abs(COALESCE(sum(CASE WHEN ac.status = 'APPROVED' THEN ac.amount ELSE 0 END), 0.0) - mb.total_actual)
    FROM maintenance_budgets mb
    JOIN budget_lines bl ON bl.budget_id = mb.id
    LEFT JOIN actual_costs ac ON ac.budget_line_id = bl.id AND ac.is_deleted = false
    WHERE mb.id = '71000000-0000-0000-0000-000000150001'::uuid
      AND mb.is_deleted = false
      AND bl.is_deleted = false
    GROUP BY mb.total_actual
) < 0.01 THEN 1 ELSE 0 END AS validate_nav_budget_actual_total;

SELECT 1 / CASE WHEN (
    SELECT abs(COALESCE(sum(CASE WHEN ac.status = 'PENDING' THEN ac.amount ELSE 0 END), 0.0) - mb.total_committed)
    FROM maintenance_budgets mb
    JOIN budget_lines bl ON bl.budget_id = mb.id
    LEFT JOIN actual_costs ac ON ac.budget_line_id = bl.id AND ac.is_deleted = false
    WHERE mb.id = '71000000-0000-0000-0000-000000150001'::uuid
      AND mb.is_deleted = false
      AND bl.is_deleted = false
    GROUP BY mb.total_committed
) < 0.01 THEN 1 ELSE 0 END AS validate_nav_budget_committed_total;

SELECT 1 / CASE WHEN (
    SELECT count(*)
    FROM actual_costs ac
    JOIN budget_lines bl ON bl.id = ac.budget_line_id
    WHERE bl.budget_id = '71000000-0000-0000-0000-000000150001'::uuid
      AND ac.is_deleted = false
      AND bl.is_deleted = false
) >= 18 THEN 1 ELSE 0 END AS validate_nav_actual_cost_count;

SELECT 1 / CASE WHEN (
    SELECT count(*)
    FROM actual_costs ac
    JOIN budget_lines bl ON bl.id = ac.budget_line_id
    WHERE bl.budget_id = '71000000-0000-0000-0000-000000150001'::uuid
      AND ac.status = 'APPROVED'
      AND ac.is_deleted = false
      AND bl.is_deleted = false
) >= 10 THEN 1 ELSE 0 END AS validate_nav_approved_actual_cost_count;

SELECT 1 / CASE WHEN (
    SELECT count(*)
    FROM actual_costs ac
    JOIN budget_lines bl ON bl.id = ac.budget_line_id
    WHERE bl.budget_id = '71000000-0000-0000-0000-000000150001'::uuid
      AND ac.status = 'PENDING'
      AND ac.is_deleted = false
      AND bl.is_deleted = false
) >= 5 THEN 1 ELSE 0 END AS validate_nav_pending_actual_cost_count;

SELECT 1 / CASE WHEN (
    SELECT count(*)
    FROM actual_costs ac
    JOIN budget_lines bl ON bl.id = ac.budget_line_id
    WHERE bl.budget_id = '71000000-0000-0000-0000-000000150001'::uuid
      AND ac.status = 'REJECTED'
      AND ac.is_deleted = false
      AND bl.is_deleted = false
) >= 2 THEN 1 ELSE 0 END AS validate_nav_rejected_actual_cost_count;

SELECT 1 / CASE WHEN (
    SELECT count(*)
    FROM actual_cost_review_events acre
    JOIN actual_costs ac ON ac.id = acre.actual_cost_id
    JOIN budget_lines bl ON bl.id = ac.budget_line_id
    WHERE bl.budget_id = '71000000-0000-0000-0000-000000150001'::uuid
      AND acre.is_deleted = false
      AND ac.is_deleted = false
      AND bl.is_deleted = false
) >= (
    SELECT count(*)
    FROM actual_costs ac
    JOIN budget_lines bl ON bl.id = ac.budget_line_id
    WHERE bl.budget_id = '71000000-0000-0000-0000-000000150001'::uuid
      AND ac.is_deleted = false
      AND bl.is_deleted = false
) THEN 1 ELSE 0 END AS validate_nav_actual_cost_review_event_count;

SELECT 1 / CASE WHEN NOT EXISTS (
    SELECT 1
    FROM budget_lines bl
    WHERE bl.budget_id = '71000000-0000-0000-0000-000000150001'::uuid
      AND bl.is_deleted = false
      AND bl.actual_amount + bl.committed_amount > bl.planned_amount
) THEN 1 ELSE 0 END AS validate_nav_budget_lines_not_overspent;

SELECT 1 / CASE WHEN NOT EXISTS (
    SELECT 1
    FROM (
        SELECT code AS value FROM departments WHERE code LIKE 'NAV-AZOT%' AND is_deleted = false
        UNION ALL SELECT name FROM departments WHERE code LIKE 'NAV-AZOT%' AND is_deleted = false
        UNION ALL SELECT COALESCE(name_en, '') FROM departments WHERE code LIKE 'NAV-AZOT%' AND is_deleted = false
        UNION ALL SELECT COALESCE(name_uz, '') FROM departments WHERE code LIKE 'NAV-AZOT%' AND is_deleted = false
        UNION ALL SELECT COALESCE(description, '') FROM departments WHERE code LIKE 'NAV-AZOT%' AND is_deleted = false

        UNION ALL SELECT code FROM locations WHERE code LIKE 'NAV-LOC%' AND is_deleted = false
        UNION ALL SELECT name FROM locations WHERE code LIKE 'NAV-LOC%' AND is_deleted = false
        UNION ALL SELECT COALESCE(name_en, '') FROM locations WHERE code LIKE 'NAV-LOC%' AND is_deleted = false
        UNION ALL SELECT COALESCE(name_uz, '') FROM locations WHERE code LIKE 'NAV-LOC%' AND is_deleted = false
        UNION ALL SELECT COALESCE(description, '') FROM locations WHERE code LIKE 'NAV-LOC%' AND is_deleted = false

        UNION ALL SELECT username FROM users WHERE username LIKE 'nav.azot.%' AND is_deleted = false
        UNION ALL SELECT email FROM users WHERE username LIKE 'nav.azot.%' AND is_deleted = false
        UNION ALL SELECT full_name FROM users WHERE username LIKE 'nav.azot.%' AND is_deleted = false
        UNION ALL SELECT COALESCE(position, '') FROM users WHERE username LIKE 'nav.azot.%' AND is_deleted = false

        UNION ALL SELECT personnel_number FROM hr_employees WHERE personnel_number LIKE 'NAV-AZOT-EMP-%' AND is_deleted = false
        UNION ALL SELECT first_name FROM hr_employees WHERE personnel_number LIKE 'NAV-AZOT-EMP-%' AND is_deleted = false
        UNION ALL SELECT last_name FROM hr_employees WHERE personnel_number LIKE 'NAV-AZOT-EMP-%' AND is_deleted = false
        UNION ALL SELECT COALESCE(middle_name, '') FROM hr_employees WHERE personnel_number LIKE 'NAV-AZOT-EMP-%' AND is_deleted = false
        UNION ALL SELECT position FROM hr_employees WHERE personnel_number LIKE 'NAV-AZOT-EMP-%' AND is_deleted = false
        UNION ALL SELECT COALESCE(email, '') FROM hr_employees WHERE personnel_number LIKE 'NAV-AZOT-EMP-%' AND is_deleted = false

        UNION ALL SELECT code FROM brigades WHERE code LIKE 'NAV-%' AND is_deleted = false
        UNION ALL SELECT name FROM brigades WHERE code LIKE 'NAV-%' AND is_deleted = false
        UNION ALL SELECT COALESCE(specialization, '') FROM brigades WHERE code LIKE 'NAV-%' AND is_deleted = false

        UNION ALL SELECT code FROM warehouses WHERE code = 'NAV-WH-MAIN' AND is_deleted = false
        UNION ALL SELECT name FROM warehouses WHERE code = 'NAV-WH-MAIN' AND is_deleted = false

        UNION ALL SELECT code FROM spare_part_types WHERE code LIKE 'NAV-%' AND active = true
        UNION ALL SELECT name FROM spare_part_types WHERE code LIKE 'NAV-%' AND active = true
        UNION ALL SELECT COALESCE(description, '') FROM spare_part_types WHERE code LIKE 'NAV-%' AND active = true

        UNION ALL SELECT code FROM spare_parts WHERE code LIKE 'NAV-SP%' AND is_deleted = false
        UNION ALL SELECT name FROM spare_parts WHERE code LIKE 'NAV-SP%' AND is_deleted = false
        UNION ALL SELECT COALESCE(specification, '') FROM spare_parts WHERE code LIKE 'NAV-SP%' AND is_deleted = false
        UNION ALL SELECT COALESCE(sku, '') FROM spare_parts WHERE code LIKE 'NAV-SP%' AND is_deleted = false
        UNION ALL SELECT COALESCE(manufacturer, '') FROM spare_parts WHERE code LIKE 'NAV-SP%' AND is_deleted = false

        UNION ALL SELECT code FROM equipment_types WHERE code LIKE 'NAV-ET-%' AND is_deleted = false
        UNION ALL SELECT name FROM equipment_types WHERE code LIKE 'NAV-ET-%' AND is_deleted = false
        UNION ALL SELECT COALESCE(description, '') FROM equipment_types WHERE code LIKE 'NAV-ET-%' AND is_deleted = false

        UNION ALL SELECT code FROM equipment WHERE (code LIKE 'NAV-EQ-%' OR code LIKE 'NAV-VEH-%') AND is_deleted = false
        UNION ALL SELECT name FROM equipment WHERE (code LIKE 'NAV-EQ-%' OR code LIKE 'NAV-VEH-%') AND is_deleted = false
        UNION ALL SELECT COALESCE(description, '') FROM equipment WHERE (code LIKE 'NAV-EQ-%' OR code LIKE 'NAV-VEH-%') AND is_deleted = false
        UNION ALL SELECT COALESCE(manufacturer, '') FROM equipment WHERE (code LIKE 'NAV-EQ-%' OR code LIKE 'NAV-VEH-%') AND is_deleted = false
        UNION ALL SELECT COALESCE(model, '') FROM equipment WHERE (code LIKE 'NAV-EQ-%' OR code LIKE 'NAV-VEH-%') AND is_deleted = false
        UNION ALL SELECT COALESCE(serial_number, '') FROM equipment WHERE (code LIKE 'NAV-EQ-%' OR code LIKE 'NAV-VEH-%') AND is_deleted = false
        UNION ALL SELECT inventory_number FROM equipment WHERE (code LIKE 'NAV-EQ-%' OR code LIKE 'NAV-VEH-%') AND is_deleted = false
        UNION ALL SELECT COALESCE(technical_number, '') FROM equipment WHERE (code LIKE 'NAV-EQ-%' OR code LIKE 'NAV-VEH-%') AND is_deleted = false

        UNION ALL SELECT vd.plate_number FROM vehicle_details vd JOIN equipment e ON e.id = vd.equipment_id WHERE e.code LIKE 'NAV-VEH-%' AND e.is_deleted = false AND vd.is_deleted = false
        UNION ALL SELECT COALESCE(vd.vin, '') FROM vehicle_details vd JOIN equipment e ON e.id = vd.equipment_id WHERE e.code LIKE 'NAV-VEH-%' AND e.is_deleted = false AND vd.is_deleted = false
        UNION ALL SELECT COALESCE(vd.brand, '') FROM vehicle_details vd JOIN equipment e ON e.id = vd.equipment_id WHERE e.code LIKE 'NAV-VEH-%' AND e.is_deleted = false AND vd.is_deleted = false
        UNION ALL SELECT COALESCE(vd.model, '') FROM vehicle_details vd JOIN equipment e ON e.id = vd.equipment_id WHERE e.code LIKE 'NAV-VEH-%' AND e.is_deleted = false AND vd.is_deleted = false
        UNION ALL SELECT COALESCE(vd.body_number, '') FROM vehicle_details vd JOIN equipment e ON e.id = vd.equipment_id WHERE e.code LIKE 'NAV-VEH-%' AND e.is_deleted = false AND vd.is_deleted = false
        UNION ALL SELECT COALESCE(vd.chassis_number, '') FROM vehicle_details vd JOIN equipment e ON e.id = vd.equipment_id WHERE e.code LIKE 'NAV-VEH-%' AND e.is_deleted = false AND vd.is_deleted = false
        UNION ALL SELECT COALESCE(vd.engine_number, '') FROM vehicle_details vd JOIN equipment e ON e.id = vd.equipment_id WHERE e.code LIKE 'NAV-VEH-%' AND e.is_deleted = false AND vd.is_deleted = false
        UNION ALL SELECT COALESCE(vd.registration_certificate_number, '') FROM vehicle_details vd JOIN equipment e ON e.id = vd.equipment_id WHERE e.code LIKE 'NAV-VEH-%' AND e.is_deleted = false AND vd.is_deleted = false
        UNION ALL SELECT COALESCE(vd.insurance_policy_number, '') FROM vehicle_details vd JOIN equipment e ON e.id = vd.equipment_id WHERE e.code LIKE 'NAV-VEH-%' AND e.is_deleted = false AND vd.is_deleted = false
        UNION ALL SELECT COALESCE(vd.gps_device_id, '') FROM vehicle_details vd JOIN equipment e ON e.id = vd.equipment_id WHERE e.code LIKE 'NAV-VEH-%' AND e.is_deleted = false AND vd.is_deleted = false

        UNION ALL SELECT number FROM repair_requests WHERE number LIKE 'NAV-RR-2026-%' AND is_deleted = false
        UNION ALL SELECT title FROM repair_requests WHERE number LIKE 'NAV-RR-2026-%' AND is_deleted = false
        UNION ALL SELECT description FROM repair_requests WHERE number LIKE 'NAV-RR-2026-%' AND is_deleted = false
        UNION ALL SELECT COALESCE(close_result, '') FROM repair_requests WHERE number LIKE 'NAV-RR-2026-%' AND is_deleted = false
        UNION ALL SELECT COALESCE(rejection_reason, '') FROM repair_requests WHERE number LIKE 'NAV-RR-2026-%' AND is_deleted = false
        UNION ALL SELECT COALESCE(clarification_reason, '') FROM repair_requests WHERE number LIKE 'NAV-RR-2026-%' AND is_deleted = false
        UNION ALL SELECT COALESCE(emergency_reason, '') FROM repair_requests WHERE number LIKE 'NAV-RR-2026-%' AND is_deleted = false

        UNION ALL SELECT number FROM work_orders WHERE number LIKE 'NAV-WO-2026-%' AND is_deleted = false
        UNION ALL SELECT title FROM work_orders WHERE number LIKE 'NAV-WO-2026-%' AND is_deleted = false
        UNION ALL SELECT COALESCE(summary, '') FROM work_orders WHERE number LIKE 'NAV-WO-2026-%' AND is_deleted = false
        UNION ALL SELECT COALESCE(result, '') FROM work_orders WHERE number LIKE 'NAV-WO-2026-%' AND is_deleted = false
        UNION ALL SELECT COALESCE(closure_notes, '') FROM work_orders WHERE number LIKE 'NAV-WO-2026-%' AND is_deleted = false
        UNION ALL SELECT COALESCE(work_location_note, '') FROM work_orders WHERE number LIKE 'NAV-WO-2026-%' AND is_deleted = false

        UNION ALL SELECT code FROM defects WHERE code LIKE 'NAV-DEF-2026-%' AND is_deleted = false
        UNION ALL SELECT title FROM defects WHERE code LIKE 'NAV-DEF-2026-%' AND is_deleted = false
        UNION ALL SELECT description FROM defects WHERE code LIKE 'NAV-DEF-2026-%' AND is_deleted = false
        UNION ALL SELECT COALESCE(failure_reason, '') FROM defects WHERE code LIKE 'NAV-DEF-2026-%' AND is_deleted = false
        UNION ALL SELECT COALESCE(root_cause, '') FROM defects WHERE code LIKE 'NAV-DEF-2026-%' AND is_deleted = false
        UNION ALL SELECT COALESCE(category, '') FROM defects WHERE code LIKE 'NAV-DEF-2026-%' AND is_deleted = false
        UNION ALL SELECT COALESCE(severity, '') FROM defects WHERE code LIKE 'NAV-DEF-2026-%' AND is_deleted = false

        UNION ALL SELECT code FROM defect_lists WHERE code LIKE 'NAV-DL-2026-%' AND is_deleted = false
        UNION ALL SELECT title FROM defect_lists WHERE code LIKE 'NAV-DL-2026-%' AND is_deleted = false
        UNION ALL SELECT COALESCE(notes, '') FROM defect_lists WHERE code LIKE 'NAV-DL-2026-%' AND is_deleted = false

        UNION ALL SELECT description FROM defect_list_lines dll JOIN defect_lists dl ON dl.id = dll.defect_list_id WHERE dl.code LIKE 'NAV-DL-2026-%' AND dl.is_deleted = false AND dll.is_deleted = false
        UNION ALL SELECT COALESCE(material_specification, '') FROM defect_list_lines dll JOIN defect_lists dl ON dl.id = dll.defect_list_id WHERE dl.code LIKE 'NAV-DL-2026-%' AND dl.is_deleted = false AND dll.is_deleted = false
        UNION ALL SELECT COALESCE(work_scope, '') FROM defect_list_lines dll JOIN defect_lists dl ON dl.id = dll.defect_list_id WHERE dl.code LIKE 'NAV-DL-2026-%' AND dl.is_deleted = false AND dll.is_deleted = false

        UNION ALL SELECT COALESCE(notes, '') FROM repair_material_usages rmu JOIN work_orders wo ON wo.id = rmu.work_order_id WHERE wo.number LIKE 'NAV-WO-2026-%' AND wo.is_deleted = false AND rmu.is_deleted = false
        UNION ALL SELECT COALESCE(source_document_no, '') FROM repair_material_usages rmu JOIN work_orders wo ON wo.id = rmu.work_order_id WHERE wo.number LIKE 'NAV-WO-2026-%' AND wo.is_deleted = false AND rmu.is_deleted = false

        UNION ALL SELECT COALESCE(reference_doc_no, '') FROM warehouse_stock_ledgers WHERE reference_doc_no LIKE 'NAV-WO-2026-%' AND is_deleted = false
        UNION ALL SELECT COALESCE(notes, '') FROM warehouse_stock_ledgers WHERE reference_doc_no LIKE 'NAV-WO-2026-%' AND is_deleted = false
        UNION ALL SELECT COALESCE(idempotency_key, '') FROM warehouse_stock_ledgers WHERE reference_doc_no LIKE 'NAV-WO-2026-%' AND is_deleted = false

        UNION ALL SELECT COALESCE(document_number, '') FROM warehouse_stock_ledger_metadata WHERE document_number LIKE 'NAV-WO-2026-%' AND is_deleted = false
        UNION ALL SELECT COALESCE(source_document_no, '') FROM warehouse_stock_ledger_metadata WHERE document_number LIKE 'NAV-WO-2026-%' AND is_deleted = false
        UNION ALL SELECT COALESCE(notes, '') FROM warehouse_stock_ledger_metadata WHERE document_number LIKE 'NAV-WO-2026-%' AND is_deleted = false
        UNION ALL SELECT COALESCE(comment, '') FROM warehouse_stock_ledger_metadata WHERE document_number LIKE 'NAV-WO-2026-%' AND is_deleted = false

        UNION ALL SELECT code FROM maintenance_regulations WHERE code LIKE 'NAV-MR-%' AND is_deleted = false
        UNION ALL SELECT name FROM maintenance_regulations WHERE code LIKE 'NAV-MR-%' AND is_deleted = false
        UNION ALL SELECT COALESCE(description, '') FROM maintenance_regulations WHERE code LIKE 'NAV-MR-%' AND is_deleted = false

        UNION ALL SELECT code FROM ppr_plans WHERE code LIKE 'NAV-PPR-2026-%' AND is_deleted = false
        UNION ALL SELECT name FROM ppr_plans WHERE code LIKE 'NAV-PPR-2026-%' AND is_deleted = false
        UNION ALL SELECT COALESCE(notes, '') FROM ppr_plans WHERE code LIKE 'NAV-PPR-2026-%' AND is_deleted = false

        UNION ALL SELECT code FROM ppr_tasks WHERE code LIKE 'NAV-PPR-TASK-2026-%' AND is_deleted = false
        UNION ALL SELECT title FROM ppr_tasks WHERE code LIKE 'NAV-PPR-TASK-2026-%' AND is_deleted = false
        UNION ALL SELECT COALESCE(postpone_reason, '') FROM ppr_tasks WHERE code LIKE 'NAV-PPR-TASK-2026-%' AND is_deleted = false
        UNION ALL SELECT COALESCE(cycle_key, '') FROM ppr_tasks WHERE code LIKE 'NAV-PPR-TASK-2026-%' AND is_deleted = false

        UNION ALL SELECT code FROM cost_categories WHERE code LIKE 'NAV-COST-%' AND is_deleted = false
        UNION ALL SELECT name FROM cost_categories WHERE code LIKE 'NAV-COST-%' AND is_deleted = false
        UNION ALL SELECT COALESCE(description, '') FROM cost_categories WHERE code LIKE 'NAV-COST-%' AND is_deleted = false
        UNION ALL SELECT COALESCE(name_en, '') FROM cost_categories WHERE code LIKE 'NAV-COST-%' AND is_deleted = false
        UNION ALL SELECT COALESCE(name_uz, '') FROM cost_categories WHERE code LIKE 'NAV-COST-%' AND is_deleted = false

        UNION ALL SELECT COALESCE(description, '') FROM budget_lines WHERE budget_id = '71000000-0000-0000-0000-000000150001'::uuid AND is_deleted = false

        UNION ALL SELECT COALESCE(ac.notes, '') FROM actual_costs ac JOIN budget_lines bl ON bl.id = ac.budget_line_id WHERE bl.budget_id = '71000000-0000-0000-0000-000000150001'::uuid AND ac.is_deleted = false AND bl.is_deleted = false
        UNION ALL SELECT COALESCE(ac.review_comment, '') FROM actual_costs ac JOIN budget_lines bl ON bl.id = ac.budget_line_id WHERE bl.budget_id = '71000000-0000-0000-0000-000000150001'::uuid AND ac.is_deleted = false AND bl.is_deleted = false
        UNION ALL SELECT COALESCE(ac.correction_reason, '') FROM actual_costs ac JOIN budget_lines bl ON bl.id = ac.budget_line_id WHERE bl.budget_id = '71000000-0000-0000-0000-000000150001'::uuid AND ac.is_deleted = false AND bl.is_deleted = false
        UNION ALL SELECT COALESCE(ac.allocation_comment, '') FROM actual_costs ac JOIN budget_lines bl ON bl.id = ac.budget_line_id WHERE bl.budget_id = '71000000-0000-0000-0000-000000150001'::uuid AND ac.is_deleted = false AND bl.is_deleted = false

        UNION ALL SELECT acre.title FROM actual_cost_review_events acre JOIN actual_costs ac ON ac.id = acre.actual_cost_id JOIN budget_lines bl ON bl.id = ac.budget_line_id WHERE bl.budget_id = '71000000-0000-0000-0000-000000150001'::uuid AND acre.is_deleted = false AND ac.is_deleted = false AND bl.is_deleted = false
        UNION ALL SELECT COALESCE(acre.description, '') FROM actual_cost_review_events acre JOIN actual_costs ac ON ac.id = acre.actual_cost_id JOIN budget_lines bl ON bl.id = ac.budget_line_id WHERE bl.budget_id = '71000000-0000-0000-0000-000000150001'::uuid AND acre.is_deleted = false AND ac.is_deleted = false AND bl.is_deleted = false
        UNION ALL SELECT COALESCE(acre.handover_comment, '') FROM actual_cost_review_events acre JOIN actual_costs ac ON ac.id = acre.actual_cost_id JOIN budget_lines bl ON bl.id = ac.budget_line_id WHERE bl.budget_id = '71000000-0000-0000-0000-000000150001'::uuid AND acre.is_deleted = false AND ac.is_deleted = false AND bl.is_deleted = false
        UNION ALL SELECT COALESCE(acre.acknowledgement_comment, '') FROM actual_cost_review_events acre JOIN actual_costs ac ON ac.id = acre.actual_cost_id JOIN budget_lines bl ON bl.id = ac.budget_line_id WHERE bl.budget_id = '71000000-0000-0000-0000-000000150001'::uuid AND acre.is_deleted = false AND ac.is_deleted = false AND bl.is_deleted = false
    ) visible_values
    WHERE value ~* '(demo|test|sample|fake|lorem|mock|dummy)'
) THEN 1 ELSE 0 END AS validate_no_forbidden_visible_terms;
