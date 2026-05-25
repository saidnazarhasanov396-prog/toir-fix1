# Demo Real-Case Seed Dependency Order

Audit date: 2026-05-25

## Parent-Before-Child Principle
- Root/reference tables are inserted first, then domain masters, then transaction/history tables.
- Child rows that carry FK or strong logical references are inserted only after parent records exist.
- For realism, lifecycle tables (requests -> defects -> work orders -> execution -> costs -> approvals) should be created by service workflow, not isolated direct inserts.

## Insert Order (Recommended)

1. **Reference and policy roots**
   - `departments`, `locations`, `roles`, `certification_types`, `service_classes`, `criticality_classes`, `units_of_measurement`, `cost_categories`, `defect_categories`, `defect_severities`, `failure_reasons`, `root_causes`, `manufacturers`, `equipment_types`, `maintenance_kpis`, `financial_approval_rules`, `integration_endpoints`, `webhook_subscriptions`

2. **Organization and security**
   - `users`, `user_roles`, `hr_employees`, `user_certifications`, `brigades`, `brigade_members`, `hr_timesheet_entries`

3. **Equipment hierarchy and core assets**
   - `equipment_nodes`, `equipment`, `equipment_passports`, `vehicle_details`

4. **Dynamic passport metadata**
   - `equipment_attribute_option_sources`, `equipment_attribute_option_items`, `equipment_attribute_definitions`, `equipment_attribute_required_criticality`

5. **Dynamic values and lifecycle history**
   - `equipment_attribute_values`, `equipment_attribute_value_history`, `equipment_status_history`

6. **Maintenance standards (PPR foundations)**
   - `maintenance_templates`, `maintenance_operations`, `maintenance_regulations`, `maintenance_regulation_attribute_conditions`

7. **Warehouse and nomenclature masters**
   - `warehouses`, `materials`, `spare_parts`, `equipment_spare_parts`, `warehouse_stocks`, `warehouse_equipment_items`

8. **Planning and campaigns**
   - `planned_shutdowns`, `ppr_plans`, `ppr_tasks`, `repair_campaigns`, `repair_campaign_stages`, `sla_rules`

9. **Repair chain core**
   - `repair_requests`, `defects`, `defect_lists`, `defect_list_lines`, `work_orders`, `work_order_tasks`, `work_executions`, `labor_entries`, `safety_permits`, `completion_acts`, `repair_material_usages`

10. **Monitoring and inspection execution**
   - `inspection_routes`, `inspection_checkpoints`, `inspection_rounds`, `inspection_round_results`, `condition_readings`, `equipment_meters`, `meter_readings`, `calibration_records`, `downtime_events`, `reliability_metrics`, `oee_records`, `rcm_snapshots`, `escalation_events`

11. **Finance and procurement**
   - `maintenance_budgets`, `budget_lines`, `procurement_requests`, `procurement_request_lines`, `actual_costs`, `actual_cost_review_route_overrides`, `approval_requests`, `approval_steps`

12. **Contractor execution**
   - `contractors`, `contractor_contracts`, `contractor_works`

13. **Documents, knowledge, and user communications**
   - `uploaded_files`, `technical_documents`, `file_assets`, `knowledge_articles`, `notifications`

14. **Runtime logs (optional/non-deterministic)**
   - `audit_logs`, `integration_sync_logs`, `webhook_event_log`

15. **Warehouse transaction tail (generated while executing scenarios)**
   - `stock_movements`, `reservations`

## Delete/Reset Order (Reverse-Safe)

1. `integration_sync_logs`, `webhook_event_log`, `audit_logs`, `notifications`
2. `approval_steps`, `approval_requests`
3. `actual_cost_review_route_overrides`, `actual_costs`
4. `procurement_request_lines`, `procurement_requests`
5. `contractor_works`, `contractor_contracts`, `contractors`
6. `repair_material_usages`, `labor_entries`, `work_executions`, `completion_acts`, `safety_permits`
7. `work_order_tasks`, `work_orders`
8. `defect_list_lines`, `defect_lists`, `defects`, `repair_requests`
9. `stock_movements`, `reservations`
10. `ppr_tasks`, `ppr_plans`, `planned_shutdowns`
11. `repair_campaign_stages`, `repair_campaigns`
12. `inspection_round_results`, `inspection_rounds`, `inspection_checkpoints`, `inspection_routes`
13. `condition_readings`, `meter_readings`, `equipment_meters`, `calibration_records`
14. `equipment_status_history`, `equipment_attribute_value_history`, `equipment_attribute_values`
15. `equipment_attribute_required_criticality`, `equipment_attribute_definitions`, `equipment_attribute_option_items`, `equipment_attribute_option_sources`
16. `warehouse_equipment_items`, `warehouse_stocks`, `equipment_spare_parts`, `spare_parts`, `materials`, `warehouses`
17. `technical_documents`, `file_assets`, `uploaded_files`, `knowledge_articles`
18. `budget_lines`, `maintenance_budgets`, `financial_approval_rules`, `cost_categories`
19. `oee_records`, `downtime_events`, `reliability_metrics`, `rcm_snapshots`, `escalation_events`, `maintenance_kpis`
20. `vehicle_details`, `equipment_passports`, `equipment`, `equipment_nodes`, `equipment_types`, `manufacturers`, `criticality_classes`, `service_classes`
21. `hr_timesheet_entries`, `user_certifications`, `brigade_members`, `brigades`, `hr_employees`, `user_roles`, `users`, `roles`
22. `departments`, `locations`, `certification_types`, `defect_categories`, `defect_severities`, `failure_reasons`, `root_causes`, `units_of_measurement`, `integration_endpoints`, `webhook_subscriptions`

## Tables That Should Be Seeded Through Service/Domain Workflow
- `user_roles` (assign role API/service behavior)
- `repair_requests`, `defects`, `defect_lists`, `defect_list_lines`
- `work_orders`, `work_order_tasks`, `work_executions`, `labor_entries`, `safety_permits`, `completion_acts`, `repair_material_usages`
- `stock_movements`, `reservations`, `warehouse_equipment_items` (must reflect stock business rules)
- `ppr_tasks` (from regulation + plan generation flow)
- `procurement_requests`, `procurement_request_lines`
- `actual_costs`, `actual_cost_review_route_overrides`
- `approval_requests`, `approval_steps`
- `inspection_rounds`, `inspection_round_results`
- `equipment_status_history`, `equipment_attribute_value_history`
- `notifications`, `audit_logs`, `integration_sync_logs`, `webhook_event_log`

## Tables That Can Be Seeded Directly
- Reference dictionaries and policy roots:
  - `certification_types`, `service_classes`, `criticality_classes`, `cost_categories`, `defect_categories`, `defect_severities`, `failure_reasons`, `root_causes`, `units_of_measurement`, `manufacturers`, `equipment_types`, `maintenance_kpis`, `financial_approval_rules`
- Organization/environment masters:
  - `departments`, `locations`, `roles`, `users`, `hr_employees`, `brigades`, `warehouses`
- Static configuration masters:
  - `maintenance_templates`, `maintenance_operations`, `maintenance_regulations`, `maintenance_regulation_attribute_conditions`, `equipment_attribute_option_sources`, `equipment_attribute_option_items`, `equipment_attribute_definitions`, `integration_endpoints`, `webhook_subscriptions`
- Document catalogs:
  - `uploaded_files`, `file_assets`, `technical_documents`

## Why This Order Works
- FK-backed links explicitly enforce the sequence (`approval_steps -> approval_requests`, `work_order_tasks -> work_orders`, `defects/work_orders -> repair_requests`, `warehouse_stocks -> spare_parts`, `users/brigades/warehouses -> departments`, etc.).
- Non-FK but business-critical dependencies (for example `repair_material_usages` requiring valid stock and active work order context) are preserved by workflow-first generation.
- It prevents unreal combinations (for example closed work order without labor/materials/safety act or reservation without stock baseline).
