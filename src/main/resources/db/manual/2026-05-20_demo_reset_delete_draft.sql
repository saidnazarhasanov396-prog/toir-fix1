-- TOIR demo reset delete draft.
-- Manual runbook draft only. Do not run until backup, dry run, and approvals are complete.
-- Uses ordered DELETE statements. Role and migration history tables are intentionally untouched.
-- Transaction option: approve batch boundaries with DBA/ops based on lock risk.

-- 01. Volatile notification and integration event rows.
DELETE FROM notifications;
DELETE FROM integration_sync_logs;
DELETE FROM webhook_event_log;

-- 02. Generic approval rows.
DELETE FROM approval_steps;
DELETE FROM approval_requests;

-- 03. Knowledge and documents linked to work, defect, or equipment rows.
DELETE FROM knowledge_articles;
DELETE FROM technical_documents;
-- file_assets require coordinated filesystem handling and are not cleared in this draft.

-- 04. Finance rows.
DELETE FROM actual_cost_review_route_overrides;
DELETE FROM actual_costs;
DELETE FROM budget_lines;
DELETE FROM maintenance_budgets;

-- 05. Procurement rows.
DELETE FROM procurement_request_lines;
DELETE FROM procurement_requests;

-- 06. Contractor execution rows.
DELETE FROM contractor_works;
DELETE FROM contractor_contracts;
DELETE FROM contractors;

-- 07. Warehouse stock consumers and movements.
DELETE FROM reservations;
DELETE FROM repair_material_usages;
DELETE FROM stock_movements;

-- 08. Work execution artifacts.
DELETE FROM work_executions;
DELETE FROM labor_entries;
DELETE FROM completion_acts;
DELETE FROM safety_permits;

-- 09. Work order rows.
DELETE FROM work_order_tasks;
DELETE FROM work_orders;

-- 10. Defect rows.
DELETE FROM defect_list_lines;
DELETE FROM defect_lists;
DELETE FROM defects;

-- 11. Repair campaign and repair request rows.
DELETE FROM repair_campaign_stages;
DELETE FROM repair_campaigns;
DELETE FROM repair_requests;

-- 12. PPR rows.
DELETE FROM ppr_tasks;
DELETE FROM ppr_plans;
DELETE FROM planned_shutdowns;

-- 13. Inspection rows.
DELETE FROM inspection_round_results;
DELETE FROM inspection_rounds;
DELETE FROM inspection_checkpoints;
DELETE FROM inspection_routes;

-- 14. Condition, metering, and analytics rows.
DELETE FROM condition_readings;
DELETE FROM meter_readings;
DELETE FROM equipment_meters;
DELETE FROM calibration_records;
DELETE FROM equipment_kpis;
DELETE FROM maintenance_kpis;
DELETE FROM oee_records;
DELETE FROM downtime_events;
DELETE FROM reliability_metrics;
DELETE FROM rcm_snapshots;
DELETE FROM escalation_events;

-- 15. Equipment dependent rows.
DELETE FROM equipment_spare_parts;
DELETE FROM equipment_passports;
DELETE FROM equipment_nodes;
DELETE FROM vehicle_details;

-- 16. Warehouse and stock rows.
DELETE FROM warehouse_equipment_items;
DELETE FROM warehouse_stocks;
DELETE FROM warehouses;

-- 17. Equipment rows.
DELETE FROM equipment;

-- 18. HR and brigade rows.
DELETE FROM hr_timesheet_entries;
DELETE FROM user_certifications;
DELETE FROM brigade_members;
DELETE FROM brigades;
DELETE FROM hr_employees;

-- 19. Location and department rows.
DELETE FROM locations;
DELETE FROM departments;

-- 20. Non-admin user mappings only.
DELETE FROM user_roles ur
WHERE NOT EXISTS (
    SELECT 1
    FROM users u
    LEFT JOIN roles pr ON pr.id = u.primary_role_id
    WHERE u.id = ur.user_id
      AND u.is_deleted = false
      AND (pr.code = 'SYSTEM_ADMIN' OR u.username IN ('admin', 'system'))
);

-- 21. Non-admin users only.
DELETE FROM users u
WHERE u.is_deleted = false
  AND u.username NOT IN ('admin', 'system')
  AND NOT EXISTS (
      SELECT 1
      FROM roles pr
      WHERE pr.id = u.primary_role_id
        AND pr.code = 'SYSTEM_ADMIN'
  )
  AND NOT EXISTS (
      SELECT 1
      FROM user_roles ur
      JOIN roles r ON r.id = ur.role_id
      WHERE ur.user_id = u.id
        AND r.code = 'SYSTEM_ADMIN'
  );

-- 22. Reference data is preserved in this draft.
-- If business confirms replacement of dictionaries, add a separately reviewed reference-data script.
