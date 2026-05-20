-- Phase A0 TOIR live/staging FK, constraint, orphan, and consistency audit.
-- SELECT-only script. Do not add, drop, update, delete, or clean data from this file.
-- Run with psql against the target database, for example:
--   psql "$DATABASE_URL" -f src/main/resources/db/manual/audit_fk_orphans_20260520.sql

\echo '== Phase A0: target table existence =='
WITH target_tables(table_name) AS (
    VALUES
        ('users'), ('roles'), ('hr_employees'), ('hr_timesheet_entries'),
        ('departments'), ('brigades'), ('brigade_members'), ('locations'),
        ('equipment'), ('equipment_types'), ('vehicle_details'), ('equipment_meters'), ('meter_readings'),
        ('warehouses'), ('warehouse_stocks'), ('stock_movements'), ('warehouse_equipment_items'),
        ('repair_material_usages'), ('spare_parts'),
        ('ppr_plans'), ('ppr_tasks'), ('repair_requests'), ('work_orders'), ('work_order_tasks'),
        ('defects'), ('defect_lists'), ('defect_list_lines'),
        ('procurement_requests'), ('procurement_request_lines'), ('maintenance_budgets'), ('budget_lines'),
        ('actual_costs'), ('actual_cost_review_route_overrides'),
        ('approval_requests'), ('approval_steps'), ('inspection_routes'), ('inspection_checkpoints'),
        ('inspection_rounds'), ('inspection_round_results'), ('knowledge_articles'),
        ('maintenance_regulations'), ('cost_categories')
)
SELECT t.table_name,
       CASE WHEN c.oid IS NULL THEN 'MISSING' ELSE 'EXISTS' END AS table_status,
       COALESCE(n.nspname, current_schema()) AS schema_name
FROM target_tables t
LEFT JOIN pg_class c
       ON c.relname = t.table_name
      AND c.relkind IN ('r', 'p')
LEFT JOIN pg_namespace n
       ON n.oid = c.relnamespace
      AND n.nspname = current_schema()
ORDER BY t.table_name;

\echo '== Phase A0: existing foreign keys =='
SELECT
    conrelid::regclass::text AS child_table,
    conname AS constraint_name,
    pg_get_constraintdef(oid) AS constraint_definition
FROM pg_constraint
WHERE contype = 'f'
  AND connamespace = current_schema()::regnamespace
ORDER BY child_table, constraint_name;

\echo '== Phase A0: unique/check/primary constraints =='
SELECT
    conrelid::regclass::text AS table_name,
    conname AS constraint_name,
    contype AS constraint_type,
    pg_get_constraintdef(oid) AS constraint_definition
FROM pg_constraint
WHERE contype IN ('p', 'u', 'c')
  AND connamespace = current_schema()::regnamespace
ORDER BY table_name, constraint_type, constraint_name;

\echo '== Phase A0: indexes and partial indexes =='
SELECT
    schemaname,
    tablename,
    indexname,
    indexdef,
    CASE WHEN indexdef ILIKE '% WHERE %' THEN true ELSE false END AS is_partial
FROM pg_indexes
WHERE schemaname = current_schema()
  AND tablename IN (
      'users', 'roles', 'hr_employees', 'hr_timesheet_entries',
      'departments', 'brigades', 'brigade_members', 'locations',
      'equipment', 'equipment_types', 'vehicle_details', 'equipment_meters', 'meter_readings',
      'warehouses', 'warehouse_stocks', 'stock_movements', 'warehouse_equipment_items',
      'repair_material_usages', 'spare_parts',
      'ppr_plans', 'ppr_tasks', 'repair_requests', 'work_orders', 'work_order_tasks',
      'defects', 'defect_lists', 'defect_list_lines',
      'procurement_requests', 'procurement_request_lines', 'maintenance_budgets', 'budget_lines',
      'actual_costs', 'actual_cost_review_route_overrides',
      'approval_requests', 'approval_steps', 'inspection_routes', 'inspection_checkpoints',
      'inspection_rounds', 'inspection_round_results', 'knowledge_articles'
  )
ORDER BY tablename, indexname;

\echo '== Phase A0: columns, nullability, type, soft-delete presence =='
SELECT
    c.table_name,
    c.column_name,
    c.data_type,
    c.udt_name,
    c.is_nullable,
    c.column_default,
    EXISTS (
        SELECT 1
        FROM information_schema.columns sd
        WHERE sd.table_schema = c.table_schema
          AND sd.table_name = c.table_name
          AND sd.column_name = 'is_deleted'
    ) AS table_has_is_deleted
FROM information_schema.columns c
WHERE c.table_schema = current_schema()
  AND c.table_name IN (
      'users', 'roles', 'hr_employees', 'hr_timesheet_entries',
      'departments', 'brigades', 'brigade_members', 'locations',
      'equipment', 'equipment_types', 'vehicle_details', 'equipment_meters', 'meter_readings',
      'warehouses', 'warehouse_stocks', 'stock_movements', 'warehouse_equipment_items',
      'repair_material_usages', 'spare_parts',
      'ppr_plans', 'ppr_tasks', 'repair_requests', 'work_orders', 'work_order_tasks',
      'defects', 'defect_lists', 'defect_list_lines',
      'procurement_requests', 'procurement_request_lines', 'maintenance_budgets', 'budget_lines',
      'actual_costs', 'actual_cost_review_route_overrides',
      'approval_requests', 'approval_steps', 'inspection_routes', 'inspection_checkpoints',
      'inspection_rounds', 'inspection_round_results', 'knowledge_articles'
  )
ORDER BY c.table_name, c.ordinal_position;

\echo '== Phase A0: orphan checks =='
WITH orphan_checks AS (
    SELECT 'hr_employees.user_id -> users.id' AS relation_name, count(*) AS orphan_count,
           ARRAY(SELECT c.id::text FROM hr_employees c LEFT JOIN users p ON p.id = c.user_id WHERE c.user_id IS NOT NULL AND p.id IS NULL LIMIT 10) AS sample_child_ids,
           ARRAY(SELECT DISTINCT c.user_id::text FROM hr_employees c LEFT JOIN users p ON p.id = c.user_id WHERE c.user_id IS NOT NULL AND p.id IS NULL LIMIT 10) AS sample_missing_reference_ids
    FROM hr_employees c LEFT JOIN users p ON p.id = c.user_id WHERE c.user_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'hr_employees.department_id -> departments.id', count(*),
           ARRAY(SELECT c.id::text FROM hr_employees c LEFT JOIN departments p ON p.id = c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.department_id::text FROM hr_employees c LEFT JOIN departments p ON p.id = c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM hr_employees c LEFT JOIN departments p ON p.id = c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'hr_employees.brigade_id -> brigades.id', count(*),
           ARRAY(SELECT c.id::text FROM hr_employees c LEFT JOIN brigades p ON p.id = c.brigade_id WHERE c.brigade_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.brigade_id::text FROM hr_employees c LEFT JOIN brigades p ON p.id = c.brigade_id WHERE c.brigade_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM hr_employees c LEFT JOIN brigades p ON p.id = c.brigade_id WHERE c.brigade_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'hr_timesheet_entries.employee_id -> hr_employees.id', count(*),
           ARRAY(SELECT c.id::text FROM hr_timesheet_entries c LEFT JOIN hr_employees p ON p.id = c.employee_id WHERE c.employee_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.employee_id::text FROM hr_timesheet_entries c LEFT JOIN hr_employees p ON p.id = c.employee_id WHERE c.employee_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM hr_timesheet_entries c LEFT JOIN hr_employees p ON p.id = c.employee_id WHERE c.employee_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'hr_timesheet_entries.work_order_id -> work_orders.id', count(*),
           ARRAY(SELECT c.id::text FROM hr_timesheet_entries c LEFT JOIN work_orders p ON p.id = c.work_order_id WHERE c.work_order_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.work_order_id::text FROM hr_timesheet_entries c LEFT JOIN work_orders p ON p.id = c.work_order_id WHERE c.work_order_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM hr_timesheet_entries c LEFT JOIN work_orders p ON p.id = c.work_order_id WHERE c.work_order_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'brigades.department_id -> departments.id', count(*),
           ARRAY(SELECT c.id::text FROM brigades c LEFT JOIN departments p ON p.id = c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.department_id::text FROM brigades c LEFT JOIN departments p ON p.id = c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM brigades c LEFT JOIN departments p ON p.id = c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'brigade_members.user_id -> users.id', count(*),
           ARRAY(SELECT c.id::text FROM brigade_members c LEFT JOIN users p ON p.id = c.user_id WHERE c.user_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.user_id::text FROM brigade_members c LEFT JOIN users p ON p.id = c.user_id WHERE c.user_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM brigade_members c LEFT JOIN users p ON p.id = c.user_id WHERE c.user_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'brigade_members.brigade_id -> brigades.id', count(*),
           ARRAY(SELECT c.id::text FROM brigade_members c LEFT JOIN brigades p ON p.id = c.brigade_id WHERE c.brigade_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.brigade_id::text FROM brigade_members c LEFT JOIN brigades p ON p.id = c.brigade_id WHERE c.brigade_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM brigade_members c LEFT JOIN brigades p ON p.id = c.brigade_id WHERE c.brigade_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'locations.department_id -> departments.id', count(*),
           ARRAY(SELECT c.id::text FROM locations c LEFT JOIN departments p ON p.id = c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.department_id::text FROM locations c LEFT JOIN departments p ON p.id = c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM locations c LEFT JOIN departments p ON p.id = c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'equipment.equipment_type_id -> equipment_types.id', count(*),
           ARRAY(SELECT c.id::text FROM equipment c LEFT JOIN equipment_types p ON p.id = c.equipment_type_id WHERE c.equipment_type_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.equipment_type_id::text FROM equipment c LEFT JOIN equipment_types p ON p.id = c.equipment_type_id WHERE c.equipment_type_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM equipment c LEFT JOIN equipment_types p ON p.id = c.equipment_type_id WHERE c.equipment_type_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'equipment.department_id -> departments.id', count(*),
           ARRAY(SELECT c.id::text FROM equipment c LEFT JOIN departments p ON p.id = c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.department_id::text FROM equipment c LEFT JOIN departments p ON p.id = c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM equipment c LEFT JOIN departments p ON p.id = c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'equipment.location_id -> locations.id', count(*),
           ARRAY(SELECT c.id::text FROM equipment c LEFT JOIN locations p ON p.id = c.location_id WHERE c.location_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.location_id::text FROM equipment c LEFT JOIN locations p ON p.id = c.location_id WHERE c.location_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM equipment c LEFT JOIN locations p ON p.id = c.location_id WHERE c.location_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'equipment.parent_id -> equipment.id', count(*),
           ARRAY(SELECT c.id::text FROM equipment c LEFT JOIN equipment p ON p.id = c.parent_id WHERE c.parent_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.parent_id::text FROM equipment c LEFT JOIN equipment p ON p.id = c.parent_id WHERE c.parent_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM equipment c LEFT JOIN equipment p ON p.id = c.parent_id WHERE c.parent_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'equipment.responsible_id -> users.id (semantic check)', count(*),
           ARRAY(SELECT c.id::text FROM equipment c LEFT JOIN users p ON p.id = c.responsible_id WHERE c.responsible_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.responsible_id::text FROM equipment c LEFT JOIN users p ON p.id = c.responsible_id WHERE c.responsible_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM equipment c LEFT JOIN users p ON p.id = c.responsible_id WHERE c.responsible_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'equipment.responsible_id -> hr_employees.id (semantic check)', count(*),
           ARRAY(SELECT c.id::text FROM equipment c LEFT JOIN hr_employees p ON p.id = c.responsible_id WHERE c.responsible_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.responsible_id::text FROM equipment c LEFT JOIN hr_employees p ON p.id = c.responsible_id WHERE c.responsible_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM equipment c LEFT JOIN hr_employees p ON p.id = c.responsible_id WHERE c.responsible_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'vehicle_details.equipment_id -> equipment.id', count(*),
           ARRAY(SELECT c.id::text FROM vehicle_details c LEFT JOIN equipment p ON p.id = c.equipment_id WHERE c.equipment_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.equipment_id::text FROM vehicle_details c LEFT JOIN equipment p ON p.id = c.equipment_id WHERE c.equipment_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM vehicle_details c LEFT JOIN equipment p ON p.id = c.equipment_id WHERE c.equipment_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'warehouses.department_id -> departments.id', count(*),
           ARRAY(SELECT c.id::text FROM warehouses c LEFT JOIN departments p ON p.id = c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.department_id::text FROM warehouses c LEFT JOIN departments p ON p.id = c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM warehouses c LEFT JOIN departments p ON p.id = c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'warehouses.location_id -> locations.id', count(*),
           ARRAY(SELECT c.id::text FROM warehouses c LEFT JOIN locations p ON p.id = c.location_id WHERE c.location_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.location_id::text FROM warehouses c LEFT JOIN locations p ON p.id = c.location_id WHERE c.location_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM warehouses c LEFT JOIN locations p ON p.id = c.location_id WHERE c.location_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'warehouses.responsible_id -> hr_employees.id', count(*),
           ARRAY(SELECT c.id::text FROM warehouses c LEFT JOIN hr_employees p ON p.id = c.responsible_id WHERE c.responsible_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.responsible_id::text FROM warehouses c LEFT JOIN hr_employees p ON p.id = c.responsible_id WHERE c.responsible_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM warehouses c LEFT JOIN hr_employees p ON p.id = c.responsible_id WHERE c.responsible_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'warehouse_stocks.warehouse_id -> warehouses.id', count(*),
           ARRAY(SELECT c.id::text FROM warehouse_stocks c LEFT JOIN warehouses p ON p.id = c.warehouse_id WHERE c.warehouse_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.warehouse_id::text FROM warehouse_stocks c LEFT JOIN warehouses p ON p.id = c.warehouse_id WHERE c.warehouse_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM warehouse_stocks c LEFT JOIN warehouses p ON p.id = c.warehouse_id WHERE c.warehouse_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'warehouse_stocks.spare_part_id -> spare_parts.id', count(*),
           ARRAY(SELECT c.id::text FROM warehouse_stocks c LEFT JOIN spare_parts p ON p.id = c.spare_part_id WHERE c.spare_part_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.spare_part_id::text FROM warehouse_stocks c LEFT JOIN spare_parts p ON p.id = c.spare_part_id WHERE c.spare_part_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM warehouse_stocks c LEFT JOIN spare_parts p ON p.id = c.spare_part_id WHERE c.spare_part_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'stock_movements.warehouse_id -> warehouses.id', count(*),
           ARRAY(SELECT c.id::text FROM stock_movements c LEFT JOIN warehouses p ON p.id = c.warehouse_id WHERE c.warehouse_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.warehouse_id::text FROM stock_movements c LEFT JOIN warehouses p ON p.id = c.warehouse_id WHERE c.warehouse_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM stock_movements c LEFT JOIN warehouses p ON p.id = c.warehouse_id WHERE c.warehouse_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'stock_movements.spare_part_id -> spare_parts.id', count(*),
           ARRAY(SELECT c.id::text FROM stock_movements c LEFT JOIN spare_parts p ON p.id = c.spare_part_id WHERE c.spare_part_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.spare_part_id::text FROM stock_movements c LEFT JOIN spare_parts p ON p.id = c.spare_part_id WHERE c.spare_part_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM stock_movements c LEFT JOIN spare_parts p ON p.id = c.spare_part_id WHERE c.spare_part_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'stock_movements.work_order_id -> work_orders.id', count(*),
           ARRAY(SELECT c.id::text FROM stock_movements c LEFT JOIN work_orders p ON p.id = c.work_order_id WHERE c.work_order_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.work_order_id::text FROM stock_movements c LEFT JOIN work_orders p ON p.id = c.work_order_id WHERE c.work_order_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM stock_movements c LEFT JOIN work_orders p ON p.id = c.work_order_id WHERE c.work_order_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'warehouse_equipment_items.warehouse_id -> warehouses.id', count(*),
           ARRAY(SELECT c.id::text FROM warehouse_equipment_items c LEFT JOIN warehouses p ON p.id = c.warehouse_id WHERE c.warehouse_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.warehouse_id::text FROM warehouse_equipment_items c LEFT JOIN warehouses p ON p.id = c.warehouse_id WHERE c.warehouse_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM warehouse_equipment_items c LEFT JOIN warehouses p ON p.id = c.warehouse_id WHERE c.warehouse_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'warehouse_equipment_items.equipment_id -> equipment.id', count(*),
           ARRAY(SELECT c.id::text FROM warehouse_equipment_items c LEFT JOIN equipment p ON p.id = c.equipment_id WHERE c.equipment_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.equipment_id::text FROM warehouse_equipment_items c LEFT JOIN equipment p ON p.id = c.equipment_id WHERE c.equipment_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM warehouse_equipment_items c LEFT JOIN equipment p ON p.id = c.equipment_id WHERE c.equipment_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'repair_material_usages.work_order_id -> work_orders.id', count(*),
           ARRAY(SELECT c.id::text FROM repair_material_usages c LEFT JOIN work_orders p ON p.id = c.work_order_id WHERE c.work_order_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.work_order_id::text FROM repair_material_usages c LEFT JOIN work_orders p ON p.id = c.work_order_id WHERE c.work_order_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM repair_material_usages c LEFT JOIN work_orders p ON p.id = c.work_order_id WHERE c.work_order_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'repair_material_usages.warehouse_id -> warehouses.id', count(*),
           ARRAY(SELECT c.id::text FROM repair_material_usages c LEFT JOIN warehouses p ON p.id = c.warehouse_id WHERE c.warehouse_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.warehouse_id::text FROM repair_material_usages c LEFT JOIN warehouses p ON p.id = c.warehouse_id WHERE c.warehouse_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM repair_material_usages c LEFT JOIN warehouses p ON p.id = c.warehouse_id WHERE c.warehouse_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'repair_material_usages.spare_part_id -> spare_parts.id', count(*),
           ARRAY(SELECT c.id::text FROM repair_material_usages c LEFT JOIN spare_parts p ON p.id = c.spare_part_id WHERE c.spare_part_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.spare_part_id::text FROM repair_material_usages c LEFT JOIN spare_parts p ON p.id = c.spare_part_id WHERE c.spare_part_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM repair_material_usages c LEFT JOIN spare_parts p ON p.id = c.spare_part_id WHERE c.spare_part_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'ppr_plans.department_id -> departments.id', count(*),
           ARRAY(SELECT c.id::text FROM ppr_plans c LEFT JOIN departments p ON p.id = c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.department_id::text FROM ppr_plans c LEFT JOIN departments p ON p.id = c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM ppr_plans c LEFT JOIN departments p ON p.id = c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'ppr_tasks.plan_id -> ppr_plans.id', count(*),
           ARRAY(SELECT c.id::text FROM ppr_tasks c LEFT JOIN ppr_plans p ON p.id = c.plan_id WHERE c.plan_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.plan_id::text FROM ppr_tasks c LEFT JOIN ppr_plans p ON p.id = c.plan_id WHERE c.plan_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM ppr_tasks c LEFT JOIN ppr_plans p ON p.id = c.plan_id WHERE c.plan_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'ppr_tasks.equipment_id -> equipment.id', count(*),
           ARRAY(SELECT c.id::text FROM ppr_tasks c LEFT JOIN equipment p ON p.id = c.equipment_id WHERE c.equipment_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.equipment_id::text FROM ppr_tasks c LEFT JOIN equipment p ON p.id = c.equipment_id WHERE c.equipment_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM ppr_tasks c LEFT JOIN equipment p ON p.id = c.equipment_id WHERE c.equipment_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'ppr_tasks.regulation_id -> maintenance_regulations.id', count(*),
           ARRAY(SELECT c.id::text FROM ppr_tasks c LEFT JOIN maintenance_regulations p ON p.id = c.regulation_id WHERE c.regulation_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.regulation_id::text FROM ppr_tasks c LEFT JOIN maintenance_regulations p ON p.id = c.regulation_id WHERE c.regulation_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM ppr_tasks c LEFT JOIN maintenance_regulations p ON p.id = c.regulation_id WHERE c.regulation_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'repair_requests.equipment_id -> equipment.id', count(*),
           ARRAY(SELECT c.id::text FROM repair_requests c LEFT JOIN equipment p ON p.id = c.equipment_id WHERE c.equipment_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.equipment_id::text FROM repair_requests c LEFT JOIN equipment p ON p.id = c.equipment_id WHERE c.equipment_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM repair_requests c LEFT JOIN equipment p ON p.id = c.equipment_id WHERE c.equipment_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'repair_requests.department_id -> departments.id', count(*),
           ARRAY(SELECT c.id::text FROM repair_requests c LEFT JOIN departments p ON p.id = c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.department_id::text FROM repair_requests c LEFT JOIN departments p ON p.id = c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM repair_requests c LEFT JOIN departments p ON p.id = c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'repair_requests.location_id -> locations.id', count(*),
           ARRAY(SELECT c.id::text FROM repair_requests c LEFT JOIN locations p ON p.id = c.location_id WHERE c.location_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.location_id::text FROM repair_requests c LEFT JOIN locations p ON p.id = c.location_id WHERE c.location_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM repair_requests c LEFT JOIN locations p ON p.id = c.location_id WHERE c.location_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'repair_requests.reporter_id -> users.id (semantic check)', count(*),
           ARRAY(SELECT c.id::text FROM repair_requests c LEFT JOIN users p ON p.id = c.reporter_id WHERE c.reporter_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.reporter_id::text FROM repair_requests c LEFT JOIN users p ON p.id = c.reporter_id WHERE c.reporter_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM repair_requests c LEFT JOIN users p ON p.id = c.reporter_id WHERE c.reporter_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'repair_requests.reporter_id -> hr_employees.id (semantic check)', count(*),
           ARRAY(SELECT c.id::text FROM repair_requests c LEFT JOIN hr_employees p ON p.id = c.reporter_id WHERE c.reporter_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.reporter_id::text FROM repair_requests c LEFT JOIN hr_employees p ON p.id = c.reporter_id WHERE c.reporter_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM repair_requests c LEFT JOIN hr_employees p ON p.id = c.reporter_id WHERE c.reporter_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'repair_requests.assigned_to_id -> users.id (semantic check)', count(*),
           ARRAY(SELECT c.id::text FROM repair_requests c LEFT JOIN users p ON p.id = c.assigned_to_id WHERE c.assigned_to_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.assigned_to_id::text FROM repair_requests c LEFT JOIN users p ON p.id = c.assigned_to_id WHERE c.assigned_to_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM repair_requests c LEFT JOIN users p ON p.id = c.assigned_to_id WHERE c.assigned_to_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'repair_requests.assigned_to_id -> hr_employees.id (semantic check)', count(*),
           ARRAY(SELECT c.id::text FROM repair_requests c LEFT JOIN hr_employees p ON p.id = c.assigned_to_id WHERE c.assigned_to_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.assigned_to_id::text FROM repair_requests c LEFT JOIN hr_employees p ON p.id = c.assigned_to_id WHERE c.assigned_to_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM repair_requests c LEFT JOIN hr_employees p ON p.id = c.assigned_to_id WHERE c.assigned_to_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'work_orders.equipment_id -> equipment.id', count(*),
           ARRAY(SELECT c.id::text FROM work_orders c LEFT JOIN equipment p ON p.id = c.equipment_id WHERE c.equipment_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.equipment_id::text FROM work_orders c LEFT JOIN equipment p ON p.id = c.equipment_id WHERE c.equipment_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM work_orders c LEFT JOIN equipment p ON p.id = c.equipment_id WHERE c.equipment_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'work_orders.department_id -> departments.id', count(*),
           ARRAY(SELECT c.id::text FROM work_orders c LEFT JOIN departments p ON p.id = c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.department_id::text FROM work_orders c LEFT JOIN departments p ON p.id = c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM work_orders c LEFT JOIN departments p ON p.id = c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'work_orders.repair_request_id -> repair_requests.id', count(*),
           ARRAY(SELECT c.id::text FROM work_orders c LEFT JOIN repair_requests p ON p.id = c.repair_request_id WHERE c.repair_request_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.repair_request_id::text FROM work_orders c LEFT JOIN repair_requests p ON p.id = c.repair_request_id WHERE c.repair_request_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM work_orders c LEFT JOIN repair_requests p ON p.id = c.repair_request_id WHERE c.repair_request_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'work_orders.defect_id -> defects.id', count(*),
           ARRAY(SELECT c.id::text FROM work_orders c LEFT JOIN defects p ON p.id = c.defect_id WHERE c.defect_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.defect_id::text FROM work_orders c LEFT JOIN defects p ON p.id = c.defect_id WHERE c.defect_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM work_orders c LEFT JOIN defects p ON p.id = c.defect_id WHERE c.defect_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'work_orders.ppr_task_id -> ppr_tasks.id', count(*),
           ARRAY(SELECT c.id::text FROM work_orders c LEFT JOIN ppr_tasks p ON p.id = c.ppr_task_id WHERE c.ppr_task_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.ppr_task_id::text FROM work_orders c LEFT JOIN ppr_tasks p ON p.id = c.ppr_task_id WHERE c.ppr_task_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM work_orders c LEFT JOIN ppr_tasks p ON p.id = c.ppr_task_id WHERE c.ppr_task_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'work_orders.warehouse_id -> warehouses.id', count(*),
           ARRAY(SELECT c.id::text FROM work_orders c LEFT JOIN warehouses p ON p.id = c.warehouse_id WHERE c.warehouse_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.warehouse_id::text FROM work_orders c LEFT JOIN warehouses p ON p.id = c.warehouse_id WHERE c.warehouse_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM work_orders c LEFT JOIN warehouses p ON p.id = c.warehouse_id WHERE c.warehouse_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'work_orders.replacement_equipment_id -> equipment.id', count(*),
           ARRAY(SELECT c.id::text FROM work_orders c LEFT JOIN equipment p ON p.id = c.replacement_equipment_id WHERE c.replacement_equipment_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.replacement_equipment_id::text FROM work_orders c LEFT JOIN equipment p ON p.id = c.replacement_equipment_id WHERE c.replacement_equipment_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM work_orders c LEFT JOIN equipment p ON p.id = c.replacement_equipment_id WHERE c.replacement_equipment_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'work_order_tasks.work_order_id -> work_orders.id', count(*),
           ARRAY(SELECT c.id::text FROM work_order_tasks c LEFT JOIN work_orders p ON p.id = c.work_order_id WHERE c.work_order_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.work_order_id::text FROM work_order_tasks c LEFT JOIN work_orders p ON p.id = c.work_order_id WHERE c.work_order_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM work_order_tasks c LEFT JOIN work_orders p ON p.id = c.work_order_id WHERE c.work_order_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'work_order_tasks.assigned_to_id -> users.id (semantic check)', count(*),
           ARRAY(SELECT c.id::text FROM work_order_tasks c LEFT JOIN users p ON p.id = c.assigned_to_id WHERE c.assigned_to_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.assigned_to_id::text FROM work_order_tasks c LEFT JOIN users p ON p.id = c.assigned_to_id WHERE c.assigned_to_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM work_order_tasks c LEFT JOIN users p ON p.id = c.assigned_to_id WHERE c.assigned_to_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'work_order_tasks.assigned_to_id -> hr_employees.id (semantic check)', count(*),
           ARRAY(SELECT c.id::text FROM work_order_tasks c LEFT JOIN hr_employees p ON p.id = c.assigned_to_id WHERE c.assigned_to_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.assigned_to_id::text FROM work_order_tasks c LEFT JOIN hr_employees p ON p.id = c.assigned_to_id WHERE c.assigned_to_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM work_order_tasks c LEFT JOIN hr_employees p ON p.id = c.assigned_to_id WHERE c.assigned_to_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'defects.equipment_id -> equipment.id', count(*),
           ARRAY(SELECT c.id::text FROM defects c LEFT JOIN equipment p ON p.id = c.equipment_id WHERE c.equipment_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.equipment_id::text FROM defects c LEFT JOIN equipment p ON p.id = c.equipment_id WHERE c.equipment_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM defects c LEFT JOIN equipment p ON p.id = c.equipment_id WHERE c.equipment_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'defects.repair_request_id -> repair_requests.id', count(*),
           ARRAY(SELECT c.id::text FROM defects c LEFT JOIN repair_requests p ON p.id = c.repair_request_id WHERE c.repair_request_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.repair_request_id::text FROM defects c LEFT JOIN repair_requests p ON p.id = c.repair_request_id WHERE c.repair_request_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM defects c LEFT JOIN repair_requests p ON p.id = c.repair_request_id WHERE c.repair_request_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'defect_lists.equipment_id -> equipment.id', count(*),
           ARRAY(SELECT c.id::text FROM defect_lists c LEFT JOIN equipment p ON p.id = c.equipment_id WHERE c.equipment_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.equipment_id::text FROM defect_lists c LEFT JOIN equipment p ON p.id = c.equipment_id WHERE c.equipment_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM defect_lists c LEFT JOIN equipment p ON p.id = c.equipment_id WHERE c.equipment_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'defect_lists.repair_request_id -> repair_requests.id', count(*),
           ARRAY(SELECT c.id::text FROM defect_lists c LEFT JOIN repair_requests p ON p.id = c.repair_request_id WHERE c.repair_request_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.repair_request_id::text FROM defect_lists c LEFT JOIN repair_requests p ON p.id = c.repair_request_id WHERE c.repair_request_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM defect_lists c LEFT JOIN repair_requests p ON p.id = c.repair_request_id WHERE c.repair_request_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'defect_lists.work_order_id -> work_orders.id', count(*),
           ARRAY(SELECT c.id::text FROM defect_lists c LEFT JOIN work_orders p ON p.id = c.work_order_id WHERE c.work_order_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.work_order_id::text FROM defect_lists c LEFT JOIN work_orders p ON p.id = c.work_order_id WHERE c.work_order_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM defect_lists c LEFT JOIN work_orders p ON p.id = c.work_order_id WHERE c.work_order_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'defect_list_lines.defect_list_id -> defect_lists.id', count(*),
           ARRAY(SELECT c.id::text FROM defect_list_lines c LEFT JOIN defect_lists p ON p.id = c.defect_list_id WHERE c.defect_list_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.defect_list_id::text FROM defect_list_lines c LEFT JOIN defect_lists p ON p.id = c.defect_list_id WHERE c.defect_list_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM defect_list_lines c LEFT JOIN defect_lists p ON p.id = c.defect_list_id WHERE c.defect_list_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'defect_list_lines.defect_id -> defects.id', count(*),
           ARRAY(SELECT c.id::text FROM defect_list_lines c LEFT JOIN defects p ON p.id = c.defect_id WHERE c.defect_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.defect_id::text FROM defect_list_lines c LEFT JOIN defects p ON p.id = c.defect_id WHERE c.defect_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM defect_list_lines c LEFT JOIN defects p ON p.id = c.defect_id WHERE c.defect_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'defect_list_lines.spare_part_id -> spare_parts.id', count(*),
           ARRAY(SELECT c.id::text FROM defect_list_lines c LEFT JOIN spare_parts p ON p.id = c.spare_part_id WHERE c.spare_part_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.spare_part_id::text FROM defect_list_lines c LEFT JOIN spare_parts p ON p.id = c.spare_part_id WHERE c.spare_part_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM defect_list_lines c LEFT JOIN spare_parts p ON p.id = c.spare_part_id WHERE c.spare_part_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'procurement_requests.department_id -> departments.id', count(*),
           ARRAY(SELECT c.id::text FROM procurement_requests c LEFT JOIN departments p ON p.id = c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.department_id::text FROM procurement_requests c LEFT JOIN departments p ON p.id = c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM procurement_requests c LEFT JOIN departments p ON p.id = c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'procurement_requests.warehouse_id -> warehouses.id', count(*),
           ARRAY(SELECT c.id::text FROM procurement_requests c LEFT JOIN warehouses p ON p.id = c.warehouse_id WHERE c.warehouse_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.warehouse_id::text FROM procurement_requests c LEFT JOIN warehouses p ON p.id = c.warehouse_id WHERE c.warehouse_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM procurement_requests c LEFT JOIN warehouses p ON p.id = c.warehouse_id WHERE c.warehouse_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'procurement_requests.requested_by -> users.id (semantic check)', count(*),
           ARRAY(SELECT c.id::text FROM procurement_requests c LEFT JOIN users p ON p.id = c.requested_by WHERE c.requested_by IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.requested_by::text FROM procurement_requests c LEFT JOIN users p ON p.id = c.requested_by WHERE c.requested_by IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM procurement_requests c LEFT JOIN users p ON p.id = c.requested_by WHERE c.requested_by IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'procurement_requests.requested_by -> hr_employees.id (semantic check)', count(*),
           ARRAY(SELECT c.id::text FROM procurement_requests c LEFT JOIN hr_employees p ON p.id = c.requested_by WHERE c.requested_by IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.requested_by::text FROM procurement_requests c LEFT JOIN hr_employees p ON p.id = c.requested_by WHERE c.requested_by IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM procurement_requests c LEFT JOIN hr_employees p ON p.id = c.requested_by WHERE c.requested_by IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'procurement_requests.approved_by -> users.id (semantic check)', count(*),
           ARRAY(SELECT c.id::text FROM procurement_requests c LEFT JOIN users p ON p.id = c.approved_by WHERE c.approved_by IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.approved_by::text FROM procurement_requests c LEFT JOIN users p ON p.id = c.approved_by WHERE c.approved_by IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM procurement_requests c LEFT JOIN users p ON p.id = c.approved_by WHERE c.approved_by IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'procurement_requests.approved_by -> hr_employees.id (semantic check)', count(*),
           ARRAY(SELECT c.id::text FROM procurement_requests c LEFT JOIN hr_employees p ON p.id = c.approved_by WHERE c.approved_by IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.approved_by::text FROM procurement_requests c LEFT JOIN hr_employees p ON p.id = c.approved_by WHERE c.approved_by IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM procurement_requests c LEFT JOIN hr_employees p ON p.id = c.approved_by WHERE c.approved_by IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'procurement_request_lines.request_id -> procurement_requests.id', count(*),
           ARRAY(SELECT c.id::text FROM procurement_request_lines c LEFT JOIN procurement_requests p ON p.id = c.request_id WHERE c.request_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.request_id::text FROM procurement_request_lines c LEFT JOIN procurement_requests p ON p.id = c.request_id WHERE c.request_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM procurement_request_lines c LEFT JOIN procurement_requests p ON p.id = c.request_id WHERE c.request_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'procurement_request_lines.spare_part_id -> spare_parts.id', count(*),
           ARRAY(SELECT c.id::text FROM procurement_request_lines c LEFT JOIN spare_parts p ON p.id = c.spare_part_id WHERE c.spare_part_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.spare_part_id::text FROM procurement_request_lines c LEFT JOIN spare_parts p ON p.id = c.spare_part_id WHERE c.spare_part_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM procurement_request_lines c LEFT JOIN spare_parts p ON p.id = c.spare_part_id WHERE c.spare_part_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'maintenance_budgets.department_id -> departments.id', count(*),
           ARRAY(SELECT c.id::text FROM maintenance_budgets c LEFT JOIN departments p ON p.id = c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.department_id::text FROM maintenance_budgets c LEFT JOIN departments p ON p.id = c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM maintenance_budgets c LEFT JOIN departments p ON p.id = c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'budget_lines.budget_id -> maintenance_budgets.id', count(*),
           ARRAY(SELECT c.id::text FROM budget_lines c LEFT JOIN maintenance_budgets p ON p.id = c.budget_id WHERE c.budget_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.budget_id::text FROM budget_lines c LEFT JOIN maintenance_budgets p ON p.id = c.budget_id WHERE c.budget_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM budget_lines c LEFT JOIN maintenance_budgets p ON p.id = c.budget_id WHERE c.budget_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'budget_lines.cost_category_id -> cost_categories.id', count(*),
           ARRAY(SELECT c.id::text FROM budget_lines c LEFT JOIN cost_categories p ON p.id = c.cost_category_id WHERE c.cost_category_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.cost_category_id::text FROM budget_lines c LEFT JOIN cost_categories p ON p.id = c.cost_category_id WHERE c.cost_category_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM budget_lines c LEFT JOIN cost_categories p ON p.id = c.cost_category_id WHERE c.cost_category_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'actual_costs.work_order_id -> work_orders.id', count(*),
           ARRAY(SELECT c.id::text FROM actual_costs c LEFT JOIN work_orders p ON p.id = c.work_order_id WHERE c.work_order_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.work_order_id::text FROM actual_costs c LEFT JOIN work_orders p ON p.id = c.work_order_id WHERE c.work_order_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM actual_costs c LEFT JOIN work_orders p ON p.id = c.work_order_id WHERE c.work_order_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'actual_costs.repair_request_id -> repair_requests.id', count(*),
           ARRAY(SELECT c.id::text FROM actual_costs c LEFT JOIN repair_requests p ON p.id = c.repair_request_id WHERE c.repair_request_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.repair_request_id::text FROM actual_costs c LEFT JOIN repair_requests p ON p.id = c.repair_request_id WHERE c.repair_request_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM actual_costs c LEFT JOIN repair_requests p ON p.id = c.repair_request_id WHERE c.repair_request_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'actual_costs.budget_line_id -> budget_lines.id', count(*),
           ARRAY(SELECT c.id::text FROM actual_costs c LEFT JOIN budget_lines p ON p.id = c.budget_line_id WHERE c.budget_line_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.budget_line_id::text FROM actual_costs c LEFT JOIN budget_lines p ON p.id = c.budget_line_id WHERE c.budget_line_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM actual_costs c LEFT JOIN budget_lines p ON p.id = c.budget_line_id WHERE c.budget_line_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'actual_costs.cost_category_id -> cost_categories.id', count(*),
           ARRAY(SELECT c.id::text FROM actual_costs c LEFT JOIN cost_categories p ON p.id = c.cost_category_id WHERE c.cost_category_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.cost_category_id::text FROM actual_costs c LEFT JOIN cost_categories p ON p.id = c.cost_category_id WHERE c.cost_category_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM actual_costs c LEFT JOIN cost_categories p ON p.id = c.cost_category_id WHERE c.cost_category_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'actual_costs.reviewed_by_id -> users.id (semantic check)', count(*),
           ARRAY(SELECT c.id::text FROM actual_costs c LEFT JOIN users p ON p.id = c.reviewed_by_id WHERE c.reviewed_by_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.reviewed_by_id::text FROM actual_costs c LEFT JOIN users p ON p.id = c.reviewed_by_id WHERE c.reviewed_by_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM actual_costs c LEFT JOIN users p ON p.id = c.reviewed_by_id WHERE c.reviewed_by_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'actual_costs.reviewed_by_id -> hr_employees.id (semantic check)', count(*),
           ARRAY(SELECT c.id::text FROM actual_costs c LEFT JOIN hr_employees p ON p.id = c.reviewed_by_id WHERE c.reviewed_by_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.reviewed_by_id::text FROM actual_costs c LEFT JOIN hr_employees p ON p.id = c.reviewed_by_id WHERE c.reviewed_by_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM actual_costs c LEFT JOIN hr_employees p ON p.id = c.reviewed_by_id WHERE c.reviewed_by_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'actual_cost_review_route_overrides.actual_cost_id -> actual_costs.id', count(*),
           ARRAY(SELECT c.id::text FROM actual_cost_review_route_overrides c LEFT JOIN actual_costs p ON p.id = c.actual_cost_id WHERE c.actual_cost_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.actual_cost_id::text FROM actual_cost_review_route_overrides c LEFT JOIN actual_costs p ON p.id = c.actual_cost_id WHERE c.actual_cost_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM actual_cost_review_route_overrides c LEFT JOIN actual_costs p ON p.id = c.actual_cost_id WHERE c.actual_cost_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'actual_cost_review_route_overrides.department_id -> departments.id', count(*),
           ARRAY(SELECT c.id::text FROM actual_cost_review_route_overrides c LEFT JOIN departments p ON p.id = c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.department_id::text FROM actual_cost_review_route_overrides c LEFT JOIN departments p ON p.id = c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM actual_cost_review_route_overrides c LEFT JOIN departments p ON p.id = c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'approval_steps.request_id -> approval_requests.id', count(*),
           ARRAY(SELECT c.id::text FROM approval_steps c LEFT JOIN approval_requests p ON p.id = c.request_id WHERE c.request_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.request_id::text FROM approval_steps c LEFT JOIN approval_requests p ON p.id = c.request_id WHERE c.request_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM approval_steps c LEFT JOIN approval_requests p ON p.id = c.request_id WHERE c.request_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'inspection_routes.department_id -> departments.id', count(*),
           ARRAY(SELECT c.id::text FROM inspection_routes c LEFT JOIN departments p ON p.id = c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.department_id::text FROM inspection_routes c LEFT JOIN departments p ON p.id = c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM inspection_routes c LEFT JOIN departments p ON p.id = c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'inspection_checkpoints.route_id -> inspection_routes.id', count(*),
           ARRAY(SELECT c.id::text FROM inspection_checkpoints c LEFT JOIN inspection_routes p ON p.id = c.route_id WHERE c.route_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.route_id::text FROM inspection_checkpoints c LEFT JOIN inspection_routes p ON p.id = c.route_id WHERE c.route_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM inspection_checkpoints c LEFT JOIN inspection_routes p ON p.id = c.route_id WHERE c.route_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'inspection_checkpoints.equipment_id -> equipment.id', count(*),
           ARRAY(SELECT c.id::text FROM inspection_checkpoints c LEFT JOIN equipment p ON p.id = c.equipment_id WHERE c.equipment_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.equipment_id::text FROM inspection_checkpoints c LEFT JOIN equipment p ON p.id = c.equipment_id WHERE c.equipment_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM inspection_checkpoints c LEFT JOIN equipment p ON p.id = c.equipment_id WHERE c.equipment_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'inspection_checkpoints.location_id -> locations.id', count(*),
           ARRAY(SELECT c.id::text FROM inspection_checkpoints c LEFT JOIN locations p ON p.id = c.location_id WHERE c.location_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.location_id::text FROM inspection_checkpoints c LEFT JOIN locations p ON p.id = c.location_id WHERE c.location_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM inspection_checkpoints c LEFT JOIN locations p ON p.id = c.location_id WHERE c.location_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'inspection_rounds.route_id -> inspection_routes.id', count(*),
           ARRAY(SELECT c.id::text FROM inspection_rounds c LEFT JOIN inspection_routes p ON p.id = c.route_id WHERE c.route_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.route_id::text FROM inspection_rounds c LEFT JOIN inspection_routes p ON p.id = c.route_id WHERE c.route_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM inspection_rounds c LEFT JOIN inspection_routes p ON p.id = c.route_id WHERE c.route_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'inspection_round_results.round_id -> inspection_rounds.id', count(*),
           ARRAY(SELECT c.id::text FROM inspection_round_results c LEFT JOIN inspection_rounds p ON p.id = c.round_id WHERE c.round_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.round_id::text FROM inspection_round_results c LEFT JOIN inspection_rounds p ON p.id = c.round_id WHERE c.round_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM inspection_round_results c LEFT JOIN inspection_rounds p ON p.id = c.round_id WHERE c.round_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'inspection_round_results.checkpoint_id -> inspection_checkpoints.id', count(*),
           ARRAY(SELECT c.id::text FROM inspection_round_results c LEFT JOIN inspection_checkpoints p ON p.id = c.checkpoint_id WHERE c.checkpoint_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.checkpoint_id::text FROM inspection_round_results c LEFT JOIN inspection_checkpoints p ON p.id = c.checkpoint_id WHERE c.checkpoint_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM inspection_round_results c LEFT JOIN inspection_checkpoints p ON p.id = c.checkpoint_id WHERE c.checkpoint_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'inspection_round_results.defect_id -> defects.id', count(*),
           ARRAY(SELECT c.id::text FROM inspection_round_results c LEFT JOIN defects p ON p.id = c.defect_id WHERE c.defect_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.defect_id::text FROM inspection_round_results c LEFT JOIN defects p ON p.id = c.defect_id WHERE c.defect_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM inspection_round_results c LEFT JOIN defects p ON p.id = c.defect_id WHERE c.defect_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'knowledge_articles.equipment_id -> equipment.id', count(*),
           ARRAY(SELECT c.id::text FROM knowledge_articles c LEFT JOIN equipment p ON p.id = c.equipment_id WHERE c.equipment_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.equipment_id::text FROM knowledge_articles c LEFT JOIN equipment p ON p.id = c.equipment_id WHERE c.equipment_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM knowledge_articles c LEFT JOIN equipment p ON p.id = c.equipment_id WHERE c.equipment_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'knowledge_articles.defect_id -> defects.id', count(*),
           ARRAY(SELECT c.id::text FROM knowledge_articles c LEFT JOIN defects p ON p.id = c.defect_id WHERE c.defect_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.defect_id::text FROM knowledge_articles c LEFT JOIN defects p ON p.id = c.defect_id WHERE c.defect_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM knowledge_articles c LEFT JOIN defects p ON p.id = c.defect_id WHERE c.defect_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'knowledge_articles.work_order_id -> work_orders.id', count(*),
           ARRAY(SELECT c.id::text FROM knowledge_articles c LEFT JOIN work_orders p ON p.id = c.work_order_id WHERE c.work_order_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.work_order_id::text FROM knowledge_articles c LEFT JOIN work_orders p ON p.id = c.work_order_id WHERE c.work_order_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM knowledge_articles c LEFT JOIN work_orders p ON p.id = c.work_order_id WHERE c.work_order_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'knowledge_articles.author_id -> users.id (semantic check)', count(*),
           ARRAY(SELECT c.id::text FROM knowledge_articles c LEFT JOIN users p ON p.id = c.author_id WHERE c.author_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.author_id::text FROM knowledge_articles c LEFT JOIN users p ON p.id = c.author_id WHERE c.author_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM knowledge_articles c LEFT JOIN users p ON p.id = c.author_id WHERE c.author_id IS NOT NULL AND p.id IS NULL
    UNION ALL SELECT 'knowledge_articles.author_id -> hr_employees.id (semantic check)', count(*),
           ARRAY(SELECT c.id::text FROM knowledge_articles c LEFT JOIN hr_employees p ON p.id = c.author_id WHERE c.author_id IS NOT NULL AND p.id IS NULL LIMIT 10),
           ARRAY(SELECT DISTINCT c.author_id::text FROM knowledge_articles c LEFT JOIN hr_employees p ON p.id = c.author_id WHERE c.author_id IS NOT NULL AND p.id IS NULL LIMIT 10)
    FROM knowledge_articles c LEFT JOIN hr_employees p ON p.id = c.author_id WHERE c.author_id IS NOT NULL AND p.id IS NULL
)
SELECT relation_name, orphan_count, sample_child_ids, sample_missing_reference_ids
FROM orphan_checks
ORDER BY orphan_count DESC, relation_name;

\echo '== Phase A0: business consistency checks =='
WITH consistency_checks AS (
    SELECT 'equipment has department_id and active warehouse assignment' AS check_name, count(*) AS conflict_count,
           ARRAY(SELECT e.id::text FROM equipment e JOIN warehouse_equipment_items wei ON wei.equipment_id = e.id WHERE e.department_id IS NOT NULL AND wei.active = true AND COALESCE(wei.is_deleted, false) = false LIMIT 10) AS sample_ids
    FROM equipment e JOIN warehouse_equipment_items wei ON wei.equipment_id = e.id
    WHERE e.department_id IS NOT NULL AND wei.active = true AND COALESCE(wei.is_deleted, false) = false
    UNION ALL SELECT 'equipment has null department_id and no active warehouse assignment', count(*),
           ARRAY(SELECT e.id::text FROM equipment e WHERE e.department_id IS NULL AND COALESCE(e.is_deleted, false) = false AND NOT EXISTS (SELECT 1 FROM warehouse_equipment_items wei WHERE wei.equipment_id = e.id AND wei.active = true AND COALESCE(wei.is_deleted, false) = false) LIMIT 10)
    FROM equipment e
    WHERE e.department_id IS NULL AND COALESCE(e.is_deleted, false) = false
      AND NOT EXISTS (SELECT 1 FROM warehouse_equipment_items wei WHERE wei.equipment_id = e.id AND wei.active = true AND COALESCE(wei.is_deleted, false) = false)
    UNION ALL SELECT 'multiple active warehouse equipment assignments per equipment', count(*),
           ARRAY(SELECT equipment_id::text FROM warehouse_equipment_items WHERE active = true AND COALESCE(is_deleted, false) = false GROUP BY equipment_id HAVING count(*) > 1 LIMIT 10)
    FROM (SELECT equipment_id FROM warehouse_equipment_items WHERE active = true AND COALESCE(is_deleted, false) = false GROUP BY equipment_id HAVING count(*) > 1) x
    UNION ALL SELECT 'repair_request.department_id conflicts with equipment.department_id', count(*),
           ARRAY(SELECT rr.id::text FROM repair_requests rr JOIN equipment e ON e.id = rr.equipment_id WHERE e.department_id IS NOT NULL AND rr.department_id IS DISTINCT FROM e.department_id LIMIT 10)
    FROM repair_requests rr JOIN equipment e ON e.id = rr.equipment_id
    WHERE e.department_id IS NOT NULL AND rr.department_id IS DISTINCT FROM e.department_id
    UNION ALL SELECT 'work_order.department_id conflicts with equipment.department_id', count(*),
           ARRAY(SELECT wo.id::text FROM work_orders wo JOIN equipment e ON e.id = wo.equipment_id WHERE e.department_id IS NOT NULL AND wo.department_id IS DISTINCT FROM e.department_id LIMIT 10)
    FROM work_orders wo JOIN equipment e ON e.id = wo.equipment_id
    WHERE e.department_id IS NOT NULL AND wo.department_id IS DISTINCT FROM e.department_id
    UNION ALL SELECT 'work_order.department_id conflicts with repair_request.department_id', count(*),
           ARRAY(SELECT wo.id::text FROM work_orders wo JOIN repair_requests rr ON rr.id = wo.repair_request_id WHERE wo.department_id IS DISTINCT FROM rr.department_id LIMIT 10)
    FROM work_orders wo JOIN repair_requests rr ON rr.id = wo.repair_request_id
    WHERE wo.department_id IS DISTINCT FROM rr.department_id
    UNION ALL SELECT 'work_order.department_id conflicts with ppr_plan.department_id through ppr_task', count(*),
           ARRAY(SELECT wo.id::text FROM work_orders wo JOIN ppr_tasks pt ON pt.id = wo.ppr_task_id JOIN ppr_plans pp ON pp.id = pt.plan_id WHERE pp.department_id IS NOT NULL AND wo.department_id IS DISTINCT FROM pp.department_id LIMIT 10)
    FROM work_orders wo JOIN ppr_tasks pt ON pt.id = wo.ppr_task_id JOIN ppr_plans pp ON pp.id = pt.plan_id
    WHERE pp.department_id IS NOT NULL AND wo.department_id IS DISTINCT FROM pp.department_id
    UNION ALL SELECT 'defect equipment department conflicts with repair_request department', count(*),
           ARRAY(SELECT d.id::text FROM defects d JOIN equipment e ON e.id = d.equipment_id JOIN repair_requests rr ON rr.id = d.repair_request_id WHERE e.department_id IS NOT NULL AND rr.department_id IS DISTINCT FROM e.department_id LIMIT 10)
    FROM defects d JOIN equipment e ON e.id = d.equipment_id JOIN repair_requests rr ON rr.id = d.repair_request_id
    WHERE e.department_id IS NOT NULL AND rr.department_id IS DISTINCT FROM e.department_id
    UNION ALL SELECT 'defect_list linked equipment/repair_request/work_order departments conflict', count(*),
           ARRAY(SELECT id::text FROM (
               SELECT dl.id, ARRAY_REMOVE(ARRAY[e.department_id, rr.department_id, wo.department_id], NULL) AS depts
               FROM defect_lists dl
               LEFT JOIN equipment e ON e.id = dl.equipment_id
               LEFT JOIN repair_requests rr ON rr.id = dl.repair_request_id
               LEFT JOIN work_orders wo ON wo.id = dl.work_order_id
           ) x WHERE (SELECT count(DISTINCT d) FROM unnest(x.depts) d) > 1 LIMIT 10)
    FROM (
        SELECT dl.id, ARRAY_REMOVE(ARRAY[e.department_id, rr.department_id, wo.department_id], NULL) AS depts
        FROM defect_lists dl
        LEFT JOIN equipment e ON e.id = dl.equipment_id
        LEFT JOIN repair_requests rr ON rr.id = dl.repair_request_id
        LEFT JOIN work_orders wo ON wo.id = dl.work_order_id
    ) x
    WHERE (SELECT count(DISTINCT d) FROM unnest(x.depts) d) > 1
    UNION ALL SELECT 'procurement_request.department_id conflicts with warehouse.department_id', count(*),
           ARRAY(SELECT pr.id::text FROM procurement_requests pr JOIN warehouses w ON w.id = pr.warehouse_id WHERE pr.department_id IS NOT NULL AND w.department_id IS NOT NULL AND pr.department_id IS DISTINCT FROM w.department_id LIMIT 10)
    FROM procurement_requests pr JOIN warehouses w ON w.id = pr.warehouse_id
    WHERE pr.department_id IS NOT NULL AND w.department_id IS NOT NULL AND pr.department_id IS DISTINCT FROM w.department_id
    UNION ALL SELECT 'actual_cost linked work_order/repair_request/budget_line departments conflict', count(*),
           ARRAY(SELECT id::text FROM (
               SELECT ac.id, ARRAY_REMOVE(ARRAY[wo.department_id, rr.department_id, mb.department_id], NULL) AS depts
               FROM actual_costs ac
               LEFT JOIN work_orders wo ON wo.id = ac.work_order_id
               LEFT JOIN repair_requests rr ON rr.id = ac.repair_request_id
               LEFT JOIN budget_lines bl ON bl.id = ac.budget_line_id
               LEFT JOIN maintenance_budgets mb ON mb.id = bl.budget_id
           ) x WHERE (SELECT count(DISTINCT d) FROM unnest(x.depts) d) > 1 LIMIT 10)
    FROM (
        SELECT ac.id, ARRAY_REMOVE(ARRAY[wo.department_id, rr.department_id, mb.department_id], NULL) AS depts
        FROM actual_costs ac
        LEFT JOIN work_orders wo ON wo.id = ac.work_order_id
        LEFT JOIN repair_requests rr ON rr.id = ac.repair_request_id
        LEFT JOIN budget_lines bl ON bl.id = ac.budget_line_id
        LEFT JOIN maintenance_budgets mb ON mb.id = bl.budget_id
    ) x
    WHERE (SELECT count(DISTINCT d) FROM unnest(x.depts) d) > 1
    UNION ALL SELECT 'timesheet employee department conflicts with work_order department', count(*),
           ARRAY(SELECT ts.id::text FROM hr_timesheet_entries ts JOIN hr_employees emp ON emp.id = ts.employee_id JOIN work_orders wo ON wo.id = ts.work_order_id WHERE emp.department_id IS NOT NULL AND wo.department_id IS NOT NULL AND emp.department_id IS DISTINCT FROM wo.department_id LIMIT 10)
    FROM hr_timesheet_entries ts JOIN hr_employees emp ON emp.id = ts.employee_id JOIN work_orders wo ON wo.id = ts.work_order_id
    WHERE emp.department_id IS NOT NULL AND wo.department_id IS NOT NULL AND emp.department_id IS DISTINCT FROM wo.department_id
    UNION ALL SELECT 'duplicate active ppr_plans by department_id + year + month', count(*),
           ARRAY(SELECT COALESCE(department_id::text, '<NULL>') || ':' || year::text || ':' || month::text FROM ppr_plans WHERE COALESCE(is_deleted, false) = false GROUP BY department_id, year, month HAVING count(*) > 1 LIMIT 10)
    FROM (SELECT department_id, year, month FROM ppr_plans WHERE COALESCE(is_deleted, false) = false GROUP BY department_id, year, month HAVING count(*) > 1) x
    UNION ALL SELECT 'duplicate active ppr_task code', count(*),
           ARRAY(SELECT code FROM ppr_tasks WHERE code IS NOT NULL AND COALESCE(is_deleted, false) = false GROUP BY code HAVING count(*) > 1 LIMIT 10)
    FROM (SELECT code FROM ppr_tasks WHERE code IS NOT NULL AND COALESCE(is_deleted, false) = false GROUP BY code HAVING count(*) > 1) x
    UNION ALL SELECT 'duplicate active user email', count(*),
           ARRAY(SELECT lower(email) FROM users WHERE email IS NOT NULL AND COALESCE(is_deleted, false) = false GROUP BY lower(email) HAVING count(*) > 1 LIMIT 10)
    FROM (SELECT lower(email) FROM users WHERE email IS NOT NULL AND COALESCE(is_deleted, false) = false GROUP BY lower(email) HAVING count(*) > 1) x
    UNION ALL SELECT 'duplicate active user username', count(*),
           ARRAY(SELECT lower(username) FROM users WHERE username IS NOT NULL AND COALESCE(is_deleted, false) = false GROUP BY lower(username) HAVING count(*) > 1 LIMIT 10)
    FROM (SELECT lower(username) FROM users WHERE username IS NOT NULL AND COALESCE(is_deleted, false) = false GROUP BY lower(username) HAVING count(*) > 1) x
    UNION ALL SELECT 'duplicate active role code', count(*),
           ARRAY(SELECT code FROM roles WHERE code IS NOT NULL AND COALESCE(is_deleted, false) = false GROUP BY code HAVING count(*) > 1 LIMIT 10)
    FROM (SELECT code FROM roles WHERE code IS NOT NULL AND COALESCE(is_deleted, false) = false GROUP BY code HAVING count(*) > 1) x
    UNION ALL SELECT 'duplicate active equipment code', count(*),
           ARRAY(SELECT code FROM equipment WHERE code IS NOT NULL AND COALESCE(is_deleted, false) = false GROUP BY code HAVING count(*) > 1 LIMIT 10)
    FROM (SELECT code FROM equipment WHERE code IS NOT NULL AND COALESCE(is_deleted, false) = false GROUP BY code HAVING count(*) > 1) x
    UNION ALL SELECT 'duplicate active equipment inventory_number', count(*),
           ARRAY(SELECT inventory_number FROM equipment WHERE inventory_number IS NOT NULL AND COALESCE(is_deleted, false) = false GROUP BY inventory_number HAVING count(*) > 1 LIMIT 10)
    FROM (SELECT inventory_number FROM equipment WHERE inventory_number IS NOT NULL AND COALESCE(is_deleted, false) = false GROUP BY inventory_number HAVING count(*) > 1) x
    UNION ALL SELECT 'duplicate active employee.user_id', count(*),
           ARRAY(SELECT user_id::text FROM hr_employees WHERE user_id IS NOT NULL AND COALESCE(is_deleted, false) = false GROUP BY user_id HAVING count(*) > 1 LIMIT 10)
    FROM (SELECT user_id FROM hr_employees WHERE user_id IS NOT NULL AND COALESCE(is_deleted, false) = false GROUP BY user_id HAVING count(*) > 1) x
    UNION ALL SELECT 'duplicate active warehouse_stock warehouse_id + spare_part_id', count(*),
           ARRAY(SELECT warehouse_id::text || ':' || spare_part_id::text FROM warehouse_stocks WHERE COALESCE(is_deleted, false) = false GROUP BY warehouse_id, spare_part_id HAVING count(*) > 1 LIMIT 10)
    FROM (SELECT warehouse_id, spare_part_id FROM warehouse_stocks WHERE COALESCE(is_deleted, false) = false GROUP BY warehouse_id, spare_part_id HAVING count(*) > 1) x
    UNION ALL SELECT 'duplicate active brigade_members brigade_id + user_id', count(*),
           ARRAY(SELECT brigade_id::text || ':' || user_id::text FROM brigade_members WHERE COALESCE(is_deleted, false) = false GROUP BY brigade_id, user_id HAVING count(*) > 1 LIMIT 10)
    FROM (SELECT brigade_id, user_id FROM brigade_members WHERE COALESCE(is_deleted, false) = false GROUP BY brigade_id, user_id HAVING count(*) > 1) x
    UNION ALL SELECT 'actual_costs APPROVED without review markers', count(*),
           ARRAY(SELECT id::text FROM actual_costs WHERE status = 'APPROVED' AND reviewed_by_id IS NULL AND reviewed_at IS NULL LIMIT 10)
    FROM actual_costs WHERE status = 'APPROVED' AND reviewed_by_id IS NULL AND reviewed_at IS NULL
    UNION ALL SELECT 'work_order completed/closed without started_at', count(*),
           ARRAY(SELECT id::text FROM work_orders WHERE status IN ('COMPLETED', 'CLOSED') AND started_at IS NULL LIMIT 10)
    FROM work_orders WHERE status IN ('COMPLETED', 'CLOSED') AND started_at IS NULL
    UNION ALL SELECT 'work_order closed without completed_at', count(*),
           ARRAY(SELECT id::text FROM work_orders WHERE status = 'CLOSED' AND completed_at IS NULL LIMIT 10)
    FROM work_orders WHERE status = 'CLOSED' AND completed_at IS NULL
    UNION ALL SELECT 'ppr_task completed without actual_labor_hours', count(*),
           ARRAY(SELECT id::text FROM ppr_tasks WHERE status = 'COMPLETED' AND actual_labor_hours IS NULL LIMIT 10)
    FROM ppr_tasks WHERE status = 'COMPLETED' AND actual_labor_hours IS NULL
    UNION ALL SELECT 'ppr_task completed without scheduled dates', count(*),
           ARRAY(SELECT id::text FROM ppr_tasks WHERE status = 'COMPLETED' AND (scheduled_start IS NULL OR scheduled_end IS NULL) LIMIT 10)
    FROM ppr_tasks WHERE status = 'COMPLETED' AND (scheduled_start IS NULL OR scheduled_end IS NULL)
    UNION ALL SELECT 'procurement received but no matching receipt stock movement detectable', count(*),
           ARRAY(SELECT pr.id::text FROM procurement_requests pr JOIN procurement_request_lines l ON l.request_id = pr.id WHERE pr.status = 'RECEIVED' AND pr.warehouse_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM stock_movements sm WHERE sm.warehouse_id = pr.warehouse_id AND sm.spare_part_id = l.spare_part_id AND sm.type = 'RECEIPT' AND COALESCE(sm.is_deleted, false) = false) LIMIT 10)
    FROM procurement_requests pr JOIN procurement_request_lines l ON l.request_id = pr.id
    WHERE pr.status = 'RECEIVED'
      AND pr.warehouse_id IS NOT NULL
      AND NOT EXISTS (
          SELECT 1
          FROM stock_movements sm
          WHERE sm.warehouse_id = pr.warehouse_id
            AND sm.spare_part_id = l.spare_part_id
            AND sm.type = 'RECEIPT'
            AND COALESCE(sm.is_deleted, false) = false
      )
)
SELECT check_name, conflict_count, sample_ids
FROM consistency_checks
ORDER BY conflict_count DESC, check_name;

\echo '== Phase A0: lifecycle checks not detectable from current schema =='
SELECT 'timesheet approved by owner' AS check_name,
       'NOT_DETECTABLE' AS status,
       'hr_timesheet_entries has no approved_by/owner column in current entity audit' AS reason;

