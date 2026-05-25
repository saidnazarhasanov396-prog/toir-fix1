# Demo Real-Case Seed Row Count Result

Date: 2026-05-25
Source: `scripts/demo-data/99_verify_demo_data.sql`
Raw output: `logs/demo-real-case-verify-output.txt`

## Summary

- Total public base tables checked (excluding `flyway_schema_history`): 98
- `OK` (>=15 rows): 61
- `EXCLUDED_TECHNICAL`: 2
- `BELOW_TARGET` (<15 rows): 35

## Excluded technical tables

- `integration_sync_logs` (6) - runtime integration log
- `webhook_event_log` (8) - runtime webhook delivery log

## Tables below 15 rows with explanation

- `equipment_kpis` (0) - KPI module not yet scenario-seeded in this iteration
- `maintenance_kpis` (0) - KPI module not yet scenario-seeded in this iteration
- `hr_timesheet_entries` (0) - timesheet domain intentionally deferred
- `criticality_classes` (1) - finite dictionary baseline
- `defect_categories` (1) - finite dictionary baseline
- `defect_severities` (1) - finite dictionary baseline
- `failure_reasons` (1) - finite dictionary baseline
- `root_causes` (1) - finite dictionary baseline
- `equipment_attribute_option_sources` (2) - finite option source dictionary
- `units_of_measurement` (2) - finite UOM dictionary
- `maintenance_budgets` (3) - realistic monthly/department plan scope
- `ppr_plans` (3) - realistic plan granularity for demo horizon
- `warehouses` (3) - realistic warehouse topology for scenario
- `integration_endpoints` (4) - finite external systems list
- `cost_categories` (5) - finite budgeting dictionary
- `departments` (5) - realistic demo org hierarchy scope
- `service_classes` (5) - finite service-class dictionary
- `vehicle_details` (5) - only for realistic fleet subset
- `brigades` (6) - realistic brigade structure
- `brigade_members` (6) - realistic team assignment scope
- `equipment_attribute_definitions` (6) - finite dynamic passport attribute set
- `equipment_attribute_required_criticality` (6) - one mapping per attribute set
- `inspection_routes` (6) - realistic route coverage
- `certification_types` (8) - finite certification dictionary
- `maintenance_regulations` (8) - finite regulation set per equipment classes
- `webhook_subscriptions` (8) - finite integration subscriber set
- `equipment_types` (9) - finite equipment taxonomy
- `actual_cost_review_route_overrides` (10) - finite review override policies
- `defect_lists` (10) - scenario-driven, each list is high-value aggregate
- `equipment_attribute_option_items` (10) - finite options for attribute catalogs
- `approval_requests` (12) - realistic approvals volume in demo slice
- `locations` (12) - realistic location hierarchy slice
- `procurement_requests` (12) - realistic procurement cycle volume
- `users` (13) - seeded role-based users for org workflow
- `user_roles` (13) - reflects actual seeded user-role assignments

## Major business tables meeting target

Examples (>=15 rows):
- `equipment` 65
- `warehouse_stocks` 150
- `spare_parts` 60
- `repair_requests` 40
- `defects` 30
- `work_orders` 50
- `work_order_tasks` 100
- `ppr_tasks` 75
- `stock_movements` 67
- `reservations` 24
- `labor_entries` 50
- `repair_material_usages` 45
- `inspection_round_results` 120
- `notifications` 32
- `audit_logs` 32
- `uploaded_files` 30
- `technical_documents` 24

## Notes

- The row-count script is functioning and highlights true low-cardinality tables.
- If PM requires strict 15+ for all non-technical tables, additional expansion is needed for the 35 `BELOW_TARGET` tables above.
