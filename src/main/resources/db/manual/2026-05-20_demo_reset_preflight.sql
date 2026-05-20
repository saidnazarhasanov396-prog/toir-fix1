-- TOIR demo reset preflight.
-- SELECT-only. Run before any approved reset script.

SELECT
    current_database() AS database_name,
    current_user AS database_user,
    inet_server_addr() AS server_address,
    inet_server_port() AS server_port,
    now() AS checked_at;

SELECT
    current_setting('application_name', true) AS application_name,
    current_setting('app.environment', true) AS app_environment_marker,
    current_setting('toir.environment', true) AS toir_environment_marker;

SELECT installed_rank, version, description, type, script, installed_on, success
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 20;

SELECT id, code, name, is_system, is_deleted, permissions
FROM roles
WHERE code = 'SYSTEM_ADMIN'
   OR permissions @> '["*"]'::jsonb
ORDER BY code;

SELECT
    u.id AS user_id,
    u.username,
    u.email,
    u.full_name,
    u.status,
    u.is_deleted,
    pr.code AS primary_role_code,
    array_agg(DISTINCT r.code) FILTER (WHERE r.code IS NOT NULL) AS mapped_roles
FROM users u
LEFT JOIN roles pr ON pr.id = u.primary_role_id
LEFT JOIN user_roles ur ON ur.user_id = u.id
LEFT JOIN roles r ON r.id = ur.role_id
WHERE u.is_deleted = false
  AND (
      pr.code = 'SYSTEM_ADMIN'
      OR r.code = 'SYSTEM_ADMIN'
      OR u.username IN ('admin', 'system')
  )
GROUP BY u.id, u.username, u.email, u.full_name, u.status, u.is_deleted, pr.code
ORDER BY u.username;

SELECT
    u.id AS user_id,
    u.username,
    r.id AS role_id,
    r.code AS role_code,
    r.permissions
FROM user_roles ur
JOIN users u ON u.id = ur.user_id
JOIN roles r ON r.id = ur.role_id
WHERE u.is_deleted = false
  AND r.code = 'SYSTEM_ADMIN'
ORDER BY u.username;

SELECT 'notifications' AS table_name, count(*) AS row_count FROM notifications
UNION ALL SELECT 'integration_sync_logs', count(*) FROM integration_sync_logs
UNION ALL SELECT 'webhook_event_log', count(*) FROM webhook_event_log
UNION ALL SELECT 'approval_steps', count(*) FROM approval_steps
UNION ALL SELECT 'approval_requests', count(*) FROM approval_requests
UNION ALL SELECT 'actual_cost_review_route_overrides', count(*) FROM actual_cost_review_route_overrides
UNION ALL SELECT 'actual_costs', count(*) FROM actual_costs
UNION ALL SELECT 'budget_lines', count(*) FROM budget_lines
UNION ALL SELECT 'maintenance_budgets', count(*) FROM maintenance_budgets
UNION ALL SELECT 'procurement_request_lines', count(*) FROM procurement_request_lines
UNION ALL SELECT 'procurement_requests', count(*) FROM procurement_requests
UNION ALL SELECT 'contractor_works', count(*) FROM contractor_works
UNION ALL SELECT 'contractor_contracts', count(*) FROM contractor_contracts
UNION ALL SELECT 'contractors', count(*) FROM contractors
UNION ALL SELECT 'reservations', count(*) FROM reservations
UNION ALL SELECT 'repair_material_usages', count(*) FROM repair_material_usages
UNION ALL SELECT 'stock_movements', count(*) FROM stock_movements
UNION ALL SELECT 'work_executions', count(*) FROM work_executions
UNION ALL SELECT 'labor_entries', count(*) FROM labor_entries
UNION ALL SELECT 'completion_acts', count(*) FROM completion_acts
UNION ALL SELECT 'safety_permits', count(*) FROM safety_permits
UNION ALL SELECT 'work_order_tasks', count(*) FROM work_order_tasks
UNION ALL SELECT 'work_orders', count(*) FROM work_orders
UNION ALL SELECT 'defect_list_lines', count(*) FROM defect_list_lines
UNION ALL SELECT 'defect_lists', count(*) FROM defect_lists
UNION ALL SELECT 'defects', count(*) FROM defects
UNION ALL SELECT 'repair_campaign_stages', count(*) FROM repair_campaign_stages
UNION ALL SELECT 'repair_campaigns', count(*) FROM repair_campaigns
UNION ALL SELECT 'repair_requests', count(*) FROM repair_requests
UNION ALL SELECT 'ppr_tasks', count(*) FROM ppr_tasks
UNION ALL SELECT 'ppr_plans', count(*) FROM ppr_plans
UNION ALL SELECT 'planned_shutdowns', count(*) FROM planned_shutdowns
UNION ALL SELECT 'inspection_round_results', count(*) FROM inspection_round_results
UNION ALL SELECT 'inspection_rounds', count(*) FROM inspection_rounds
UNION ALL SELECT 'inspection_checkpoints', count(*) FROM inspection_checkpoints
UNION ALL SELECT 'inspection_routes', count(*) FROM inspection_routes
UNION ALL SELECT 'condition_readings', count(*) FROM condition_readings
UNION ALL SELECT 'meter_readings', count(*) FROM meter_readings
UNION ALL SELECT 'equipment_meters', count(*) FROM equipment_meters
UNION ALL SELECT 'calibration_records', count(*) FROM calibration_records
UNION ALL SELECT 'equipment_kpis', count(*) FROM equipment_kpis
UNION ALL SELECT 'maintenance_kpis', count(*) FROM maintenance_kpis
UNION ALL SELECT 'oee_records', count(*) FROM oee_records
UNION ALL SELECT 'downtime_events', count(*) FROM downtime_events
UNION ALL SELECT 'reliability_metrics', count(*) FROM reliability_metrics
UNION ALL SELECT 'rcm_snapshots', count(*) FROM rcm_snapshots
UNION ALL SELECT 'escalation_events', count(*) FROM escalation_events
UNION ALL SELECT 'knowledge_articles', count(*) FROM knowledge_articles
UNION ALL SELECT 'technical_documents', count(*) FROM technical_documents
UNION ALL SELECT 'file_assets', count(*) FROM file_assets
UNION ALL SELECT 'equipment_spare_parts', count(*) FROM equipment_spare_parts
UNION ALL SELECT 'equipment_passports', count(*) FROM equipment_passports
UNION ALL SELECT 'equipment_nodes', count(*) FROM equipment_nodes
UNION ALL SELECT 'vehicle_details', count(*) FROM vehicle_details
UNION ALL SELECT 'warehouse_equipment_items', count(*) FROM warehouse_equipment_items
UNION ALL SELECT 'warehouse_stocks', count(*) FROM warehouse_stocks
UNION ALL SELECT 'warehouses', count(*) FROM warehouses
UNION ALL SELECT 'equipment', count(*) FROM equipment
UNION ALL SELECT 'hr_timesheet_entries', count(*) FROM hr_timesheet_entries
UNION ALL SELECT 'user_certifications', count(*) FROM user_certifications
UNION ALL SELECT 'brigade_members', count(*) FROM brigade_members
UNION ALL SELECT 'brigades', count(*) FROM brigades
UNION ALL SELECT 'hr_employees', count(*) FROM hr_employees
UNION ALL SELECT 'locations', count(*) FROM locations
UNION ALL SELECT 'departments', count(*) FROM departments
UNION ALL SELECT 'non_admin_user_roles', count(*) FROM user_roles ur
WHERE NOT EXISTS (
    SELECT 1
    FROM users u
    LEFT JOIN roles pr ON pr.id = u.primary_role_id
    WHERE u.id = ur.user_id
      AND u.is_deleted = false
      AND (pr.code = 'SYSTEM_ADMIN' OR u.username IN ('admin', 'system'))
)
UNION ALL SELECT 'non_admin_users', count(*) FROM users u
WHERE u.is_deleted = false
  AND u.username NOT IN ('admin', 'system')
  AND NOT EXISTS (
      SELECT 1 FROM roles pr WHERE pr.id = u.primary_role_id AND pr.code = 'SYSTEM_ADMIN'
  )
  AND NOT EXISTS (
      SELECT 1
      FROM user_roles ur
      JOIN roles r ON r.id = ur.role_id
      WHERE ur.user_id = u.id AND r.code = 'SYSTEM_ADMIN'
  )
ORDER BY table_name;

SELECT id, code, name, system, url, is_active, sync_work_orders, sync_downtimes, sync_defects, sync_scada, sync_production
FROM integration_endpoints
WHERE is_deleted = false
  AND is_active = true
ORDER BY code;

SELECT id, code, name, target_url, is_active, events, failure_count, last_delivery_status
FROM webhook_subscriptions
WHERE is_deleted = false
  AND is_active = true
ORDER BY code;

SELECT
    to_regclass('public.demo_reset_marker') AS demo_reset_marker_table,
    to_regclass('public.environment_marker') AS environment_marker_table;
