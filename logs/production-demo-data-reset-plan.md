# Production Demo Data Reset and Realistic Seed Plan

Date: 2026-05-20

Scope: backend data reset planning only. No destructive SQL was executed, no schema was changed, no migrations or Java source files were edited.

## 1. Executive Summary

PM approval to remove garbage production data is a business prerequisite, but it is not enough to execute the reset. The technical path must still be approved against a named database target, with a fresh backup and restore test before any production action.

Current code already has bootstrap and demo seed behavior, but it is not suitable as a production reset tool:

- `DataBootstrap` creates/preserves the `SYSTEM_ADMIN` role and wildcard permission `*` (`src/main/java/com/toir/config/DataBootstrap.java:23`, `src/main/java/com/toir/config/DataBootstrap.java:58`, `src/main/java/com/toir/config/DataBootstrap.java:66`).
- Granular role permissions are seeded by `src/main/resources/db/migration/V20260519_3__seed_granular_role_permissions.sql`.
- Production config has `app.bootstrap.seed-demo-data: false` (`src/main/resources/application-prod.yml:55`).
- Current sample seeders are `dev` profile only and return early when seed tables already contain data (`src/main/java/com/toir/config/SampleDataSeeder.java:91`, `src/main/java/com/toir/config/SampleDataSeeder.java:125`, `src/main/java/com/toir/config/ExtendedDataSeeder.java:52`, `src/main/java/com/toir/config/ExtendedDataSeeder.java:103`).

Recommendation:

- Real production: use a manually approved SQL runbook executed by DBA/ops after backup, dry run on a production snapshot, and final signoff. Use ordered `DELETE` statements with explicit preservation guards, not schema changes.
- Demo/staging only: a guarded Flyway migration is acceptable only if it hard-aborts outside a confirmed demo environment marker. It must not be reusable against live production by accident.
- Rollback: restore from database backup only. Do not rely on reverse cleanup SQL.

## 2. Tables To Preserve

These tables or rows must survive the reset unless a separate written approval explicitly says otherwise.

| Preserve target | Required handling | Reason |
|---|---|---|
| `flyway_schema_history` | Never delete, truncate, or edit | Migration contract and applied history must remain intact. |
| `roles` | Preserve all system roles and permission JSON, especially `SYSTEM_ADMIN` with `*` | Required by RBAC and seeded permission migration. |
| `users` | Preserve required admin/system users; delete or soft-delete only non-admin garbage users after dependent rows are cleared | Prevent lockout and preserve production emergency access. |
| `user_roles` | Preserve admin/system role memberships; clear only memberships belonging to removed non-admin users | Prevent accidental removal of `SYSTEM_ADMIN`. |
| `departments` | Preserve only if current organization structure is approved as correct; otherwise clear/reseed after dependent tables | Departments are root scope for most demo records. |
| Reference dictionaries | Preserve if high quality; otherwise clear/reseed in dictionary batch | Includes `cost_categories`, `equipment_types`, `maintenance_regulations`, `maintenance_templates`, `maintenance_operations`, `spare_parts`, `units_of_measurement`, `manufacturers`, `materials`, `defect_categories`, `defect_severities`, `failure_reasons`, `root_causes`, `criticality_classes`, `service_classes`, `certification_types`, `sla_rules`, `financial_approval_rules`. |
| `audit_logs` | Real production: preserve or export/archive before clearing. Demo copy: can clear if approved | Audit/compliance risk. |
| `file_assets` and physical upload directory | Preserve unless attached files are also intentionally purged | DB rows and filesystem files can diverge. |
| `integration_endpoints`, `webhook_subscriptions` | Real production: preserve/disable intentionally. Demo: replace with safe placeholders only | Avoid firing real integrations from demo workflows. |

## 3. Tables To Clear

Default clear set for demo reset, subject to final table-existence verification on the target database:

| Module | Tables |
|---|---|
| Notifications/events/logs | `notifications`, `integration_sync_logs`, `webhook_event_log`; `audit_logs` only after compliance/export approval |
| Generic approvals | `approval_steps`, `approval_requests` |
| Finance | `actual_cost_review_route_overrides`, `actual_costs`, `budget_lines`, `maintenance_budgets`; `financial_approval_rules` only if reseeding reference rules |
| Procurement | `procurement_request_lines`, `procurement_requests` |
| Contractors | `contractor_works`, `contractor_contracts`, `contractors` |
| Warehouse/stock execution | `reservations`, `repair_material_usages`, `stock_movements`, `warehouse_equipment_items`, `warehouse_stocks` |
| Work execution | `work_executions`, `labor_entries`, `completion_acts`, `safety_permits`, `work_order_tasks`, `work_orders` |
| Defects | `defect_list_lines`, `defect_lists`, `defects` |
| Repair requests/campaigns | `repair_campaign_stages`, `repair_campaigns`, `repair_requests` |
| PPR | `ppr_tasks`, `ppr_plans`, `planned_shutdowns` |
| Inspection | `inspection_round_results`, `inspection_rounds`, `inspection_checkpoints`, `inspection_routes` |
| Condition/analytics | `condition_readings`, `meter_readings`, `equipment_meters`, `calibration_records`, `equipment_kpis`, `maintenance_kpis`, `oee_records`, `downtime_events`, `reliability_metrics`, `rcm_snapshots`, `escalation_events` |
| Knowledge/documents | `knowledge_articles`, `technical_documents`, `file_assets` only if upload files are handled |
| Equipment dependents | `equipment_spare_parts`, `equipment_passports`, `equipment_nodes`, `vehicle_details` |
| Equipment | `equipment` |
| Warehouses | `warehouses` after warehouse child tables are cleared |
| HR/org | `hr_timesheet_entries`, `user_certifications`, `brigade_members`, `brigades`, `hr_employees` |
| Location/org | `locations`, then `departments` only if replacing organization structure |
| Users | Non-admin `user_roles` rows and non-admin `users` only after all dependent business rows are cleared |
| Reference data | Clear only if approved as garbage; otherwise preserve and upsert missing codes. Includes maintenance templates/operations if they are demo-specific rather than approved standards |

## 4. Delete/Truncate Order

Production recommendation: use ordered `DELETE`, not `TRUNCATE`. `TRUNCATE ... CASCADE` is too broad for production because this schema has known raw UUID links and partial Flyway ownership gaps from the prior entity relationship audit. Sequence resets can be handled separately with controlled `setval` only after inserts, if codes or IDs require it.

Ordered delete strategy:

| Step | Delete group | Notes |
|---:|---|---|
| 0 | Preflight only | Confirm target DB name/host, active profile, row counts, admin user IDs, `SYSTEM_ADMIN` role ID, and backup timestamp. |
| 1 | `notifications`, `integration_sync_logs`, `webhook_event_log` | Volatile rows first. `audit_logs` only after compliance/export approval. |
| 2 | `approval_steps`, `approval_requests` | Clear approval children before approval headers. |
| 3 | `actual_cost_review_route_overrides`, `actual_costs`, `budget_lines`, `maintenance_budgets` | Finance dependents before budget headers. |
| 4 | `procurement_request_lines`, `procurement_requests` | Lines before requests. |
| 5 | `contractor_works`, `contractor_contracts`, `contractors` | Contractor execution before contractor master data if demo contractors will be replaced. |
| 6 | `reservations`, `repair_material_usages`, `stock_movements` | Clear stock consumers/movements before stock balances. |
| 7 | `work_executions`, `labor_entries`, `completion_acts`, `safety_permits` | Execution artifacts before work orders. |
| 8 | `work_order_tasks`, `work_orders` | Tasks before work order headers. |
| 9 | `defect_list_lines`, `defect_lists`, `defects` | Defect list children before lists and defects. |
| 10 | `repair_campaign_stages`, `repair_campaigns`, `repair_requests` | Campaign stages before campaigns; requests after work orders/defects that may point back to requests. |
| 11 | `ppr_tasks`, `ppr_plans`, `planned_shutdowns` | Tasks before plans. |
| 12 | `inspection_round_results`, `inspection_rounds`, `inspection_checkpoints`, `inspection_routes` | Results -> rounds -> checkpoints -> routes. |
| 13 | `condition_readings`, `meter_readings`, `equipment_meters`, `calibration_records` | Measurement data before equipment. |
| 14 | `equipment_kpis`, `maintenance_kpis`, `oee_records`, `downtime_events`, `reliability_metrics`, `rcm_snapshots`, `escalation_events` | Derived analytics before source equipment/work records. |
| 15 | `knowledge_articles`, `technical_documents`, `file_assets` if approved | Keep filesystem cleanup coordinated with `file_assets`. |
| 16 | `equipment_spare_parts`, `equipment_passports`, `equipment_nodes`, `vehicle_details` | Equipment dependent tables before equipment. |
| 17 | `warehouse_equipment_items`, `warehouse_stocks`, `warehouses` | Warehouse children before warehouses. |
| 18 | `equipment` | After all equipment-linked business records are cleared. |
| 19 | `hr_timesheet_entries`, `user_certifications`, `brigade_members`, `brigades`, `hr_employees` | Employee and brigade dependents before employees/brigades. |
| 20 | `locations` | After equipment, warehouses, inspection checkpoints, and repair requests are cleared. |
| 21 | `departments` if replacing org structure | Only if a full org reseed is approved. |
| 22 | Non-admin `user_roles`, then non-admin `users` | Use explicit `WHERE` guards preserving admin/system users. |
| 23 | Reference dictionaries/templates if approved | Clear/reseed only dictionaries confirmed as garbage, including `maintenance_templates` and `maintenance_operations` if they are not approved standards. |
| Never | `flyway_schema_history`, `roles`, admin role permissions | Must not be cleared. |

## 5. Seed Order

The seed sequence should insert from scope roots to transactional workflows:

| Step | Seed group | Expected contents |
|---:|---|---|
| 1 | Departments | Enterprise, workshops, sections; stable department codes. |
| 2 | Roles/users/employees | Preserve roles; create demo role-specific users and linked employees; assign departments and brigades. |
| 3 | Reference data | Cost categories, defect dictionaries, failure/root causes, service/criticality classes, units, manufacturers, materials, certification types, SLA/approval rules. |
| 4 | Locations | Plant areas, workshops, units, warehouses, equipment positions. |
| 5 | Equipment types | Pumps, compressors, reactors, heat exchangers, valves, instrumentation, electrical equipment, vehicles if fleet demo is required. |
| 6 | Equipment | Assets with department/location/type, hierarchy, passports, meters, calibration/condition baselines. |
| 7 | Warehouses/spare parts/stock | Warehouses, spare part catalog, stock balances, low-stock examples, warehouse equipment placements. |
| 8 | PPR plans/tasks | Current and next-month plans with scheduled tasks derived from regulations. |
| 9 | Repair requests | Requests across statuses and departments, with clear reporter/assignee semantics. |
| 10 | Work orders | Work orders from repair requests, defects, and PPR tasks; include repair/replacement examples. |
| 11 | Defects/defect lists | Defects linked to equipment/requests/work orders and list lines. |
| 12 | Procurement | Requests and lines generated from low stock and planned work. |
| 13 | Budgets/actual costs | Department budgets, lines, work-order costs, approval/review examples. |
| 14 | Approvals | Approval requests/steps tied to work orders, procurement, budgets, and actual costs. |
| 15 | Inspections | Routes, checkpoints, rounds, results, and failed-result defect creation examples. |
| 16 | Knowledge | Procedures, troubleshooting articles, and lessons learned linked to equipment/defects/work orders. |

## 6. Sample Realistic Data Model

Use a compact but credible Navoiyazot-style maintenance dataset. Current dev seeders already use relevant domain examples such as departments `NAV`, `NAV-AMM`, `NAV-UREA`, `NAV-HNO3` (`src/main/java/com/toir/config/SampleDataSeeder.java:129`), equipment codes like `NAV-AMM-CMP-01` and `NAV-HNO3-RCT-01` (`src/main/java/com/toir/config/SampleDataSeeder.java:143`), low stock examples (`src/main/java/com/toir/config/SampleDataSeeder.java:209`), and knowledge articles for compressor vibration/corrosion/metrology (`src/main/java/com/toir/config/ExtendedDataSeeder.java:127`).

Recommended dataset size:

| Area | Suggested size |
|---|---:|
| Enterprise/departments | 1 enterprise, 4 workshops, 8 sections |
| Users/employees | 18 demo users, 25 employees, 5 brigades |
| Reference dictionaries | 8 equipment types, 10 cost categories, 10 defect/failure/root-cause values, 6 certification types |
| Locations | 20 locations across plant/workshop/unit/warehouse levels |
| Equipment | 60 assets, including hierarchy examples and 6 fleet/vehicle records if fleet is shown |
| Warehouses/spare parts/stock | 3 warehouses, 80 spare parts, 180 stock rows, 40 stock movements |
| PPR | 2 monthly plans, 80 tasks across planned/approved/in-progress/completed/postponed |
| Repair requests | 35 requests across new, clarification, approved, assigned, rejected, closed |
| Work orders | 45 work orders, including 3 replacement flows and 10 completed/closed examples |
| Defects | 25 defects, 8 defect lists, 30 defect list lines |
| Procurement | 10 requests, 30 lines, including low-stock generated examples |
| Finance | 2 budgets, 15 budget lines, 30 actual costs |
| Approvals | 12 approval requests with current/past steps |
| Inspections | 5 routes, 25 checkpoints, 20 rounds, 100 results |
| Knowledge | 20 articles with tags and links to equipment/defects/work orders |
| Analytics inputs | 50 condition readings, 20 meter readings, 10 calibrations, 8 downtime events |

Role/user seed should include at minimum:

| Role | Demo user purpose |
|---|---|
| `SYSTEM_ADMIN` | Existing preserved admin only |
| `TECHNICAL_DIRECTOR` | Global overview and approvals |
| `CHIEF_MECHANIC` | Repair and work order approvals |
| `WORKSHOP_HEAD` | Department-scoped repair request/work order owner |
| `SECTION_HEAD` | Section-level task triage |
| `FOREMAN` | Brigade execution and timesheets |
| `PPR_ENGINEER` | PPR plan/task management |
| `STOREKEEPER` | Warehouse stock and issue workflow |
| `SUPPLY_SPECIALIST` | Procurement workflow |
| `ECONOMIST` | Budget and actual cost review |
| `RELIABILITY_ENGINEER` | Defects, reliability, analytics |
| `VIEWER` | Read-only scoped smoke test |

## 7. Ordered SQL Strategy

This is the strategy shape only. Do not execute until approvals, backup, and target verification are complete.

1. Preflight `SELECT` phase:
   - Confirm `current_database()`, server address, current user, application target, and maintenance window.
   - Count all clear/preserve tables.
   - Select preserved IDs for `SYSTEM_ADMIN`, wildcard permission roles, required admin users, and admin `user_roles`.
   - Confirm no connected application workers/background schedulers will write during reset.
2. Backup phase:
   - Take a full database backup.
   - Restore backup to a temporary database and run the reset there first.
   - Record pre/post row counts and smoke-test results.
3. Delete phase:
   - Use the ordered delete groups in Section 4.
   - Use explicit `WHERE` guards for `users`, `user_roles`, and any preserved reference rows.
   - Avoid `TRUNCATE`, `DROP`, `ALTER`, and edits to `flyway_schema_history`.
4. Seed phase:
   - Insert deterministic demo records using fixed codes and, preferably, fixed UUIDs for cross-row references.
   - Seed in the order from Section 5.
   - Keep role permission data untouched; assign users to existing roles.
5. Validation phase:
   - Run orphan checks from the Phase A0 script.
   - Run row counts, duplicate-code checks, login checks, and module smoke tests.
   - Verify `SYSTEM_ADMIN` still has `*`.
6. Commit/release phase:
   - For production, prefer smaller module transactions if lock time is a concern; otherwise execute as one reviewed runbook during downtime.
   - Rollback is backup restore only.

## 8. Smoke Test Scenarios After Seed

| Scenario | Expected result |
|---|---|
| Admin login | Existing admin can log in and has `SYSTEM_ADMIN`/`*`. |
| Role integrity | All seeded roles exist with granular permissions preserved from migration seed. |
| Department scope | Department-scoped user sees only allowed departments in repair/work/equipment lists. |
| Equipment registry | Equipment filters by department/location/type; equipment detail shows passport, meters, defects, docs. |
| Warehouse stock | Low-stock items appear; stock movement issue/reserve does not create negative available stock. |
| PPR | Current month plan lists tasks; a task can move through allowed planned/start/complete workflow. |
| Repair request | Create -> approve/assign -> work order path works with department/equipment consistency. |
| Work order | Approve -> start -> material issue -> complete -> close works; replacement example references warehouse and replacement equipment. |
| Defects | Defect list with lines can be approved/closed; linked defect appears on equipment detail. |
| Procurement | Low-stock procurement request can be submitted, approved, ordered, and received. |
| Finance | Actual cost appears in review queue and moves through approve/reject flow. |
| Generic approval | Only current pending approver can approve/reject. |
| Inspection | Route starts a round; checkpoint result is saved; failed checkpoint can link/create a defect. |
| Knowledge | Articles render with tags and linked equipment/defect/work order references. |
| Analytics/reports | Dashboard and exports respect same scope filters as list endpoints. |

## 9. Risks

| Risk | Why it matters | Mitigation |
|---|---|---|
| Wrong target database | This task concerns production-like data; accidental live production execution is high impact | Require explicit target confirmation and maintenance window before execution. |
| Role/admin lockout | Reset must not delete `SYSTEM_ADMIN`, `*`, admin user, or admin memberships | Preserve IDs first and validate after reset. |
| Partial Flyway ownership | Prior audit found clean DB cannot be reconstructed from Flyway alone | Use production snapshot dry run and avoid schema assumptions. |
| Raw UUID relationships | Some links may not have FK protection | Run orphan audit before and after reset. Seed with deterministic known-good references. |
| Hard delete vs soft delete mismatch | Some repositories filter `is_deleted`; hard deleting may be safer for demo but can surprise audit/compliance | Decide per environment. Real production needs compliance approval. |
| Filesystem or object storage orphaning | `file_assets` rows may point to files outside DB | Either preserve both or purge both through an approved file cleanup plan. |
| Webhooks/integrations firing | Demo seed could trigger real external systems | Disable workers/outbound integrations or replace endpoints with safe placeholders. |
| Sequences/code generators | Existing max codes may conflict with seeded demo codes | Use fixed unique code prefixes and reset sequences only after explicit review. |
| Hidden frontend assumptions | UI may expect names/status mixes not present in minimal seed | Use smoke tests covering each major page. |
| Long locks | Large deletes can lock hot tables | Run during downtime; split by module if necessary. |
| No SQL rollback | Reverse SQL will be incomplete and risky | Rollback by restoring verified backup only. |

## 10. Required Approvals

| Approval | Required decision |
|---|---|
| PM/Product owner | Confirms target data can be removed and approves demo dataset content. |
| Tech lead | Approves delete order, seed order, environment guard, and smoke-test coverage. |
| DBA/Ops | Confirms target DB, backup/restore procedure, maintenance window, and execution user. |
| Security lead | Confirms admin/user/role preservation and demo credentials policy. |
| Compliance/business owner | Approves clearing/exporting `audit_logs`, `file_assets`, and any real operational history. |
| Integration owner | Confirms webhooks/integration endpoints are disabled or safely replaced. |

## 11. Recommended Implementation Approach

Real production:

- Do not use Flyway for deleting production business data.
- Produce a human-reviewed SQL runbook with `SELECT` preflight, ordered guarded `DELETE`, deterministic `INSERT`, and validation queries.
- Execute only after backup and successful dry run on a restored production snapshot.
- Keep the app in maintenance mode or stop writers during execution.
- Preserve `flyway_schema_history`, `roles`, `SYSTEM_ADMIN`, wildcard permission `*`, required admin users, and admin role mappings.
- Treat rollback as full DB restore only.

Demo environment:

- A guarded Flyway migration can be used only for a demo database, not for real production.
- The migration must abort unless an explicit demo marker is present, for example a configured environment marker/table value controlled outside normal production.
- It should be idempotent by stable codes/UUIDs and should avoid deleting role permission seed data.
- Prefer a dedicated demo reset command or profile over reusing `SampleDataSeeder`, because current seeders are dev-only and skip when existing departments/brigades are present.

## 12. Final Implementation Batches

| Batch | Purpose | Contents |
|---:|---|---|
| A | Preflight and backup | Target verification, row counts, preserved ID capture, backup/restore validation. |
| B | Clear volatile/workflow data | Notifications, approvals, finance, procurement, work execution, defects, repair requests, PPR, inspections. |
| C | Clear asset/org data | Equipment dependents, warehouse rows, equipment, HR/brigades, locations/departments if replacing org. |
| D | Clear non-admin identity data | Non-admin role mappings and non-admin users only. |
| E | Reference reseed | Only dictionaries approved for replacement. |
| F | Demo business seed | Departments -> users/employees -> reference/location/equipment -> workflows. |
| G | Validation | Orphan audit, duplicate checks, role/admin verification, module smoke tests. |

## 13. Commands Run

Read-only commands used to prepare this plan:

```bash
git status --short
rg -n "@Entity|@Table\\(|class .*Seeder|seed-demo-data|SYSTEM_ADMIN|flyway_schema_history" src/main/java src/main/resources -S
rg -n "CREATE TABLE|ALTER TABLE|CREATE INDEX|CREATE UNIQUE INDEX" src/main/resources/db/migration -S
nl -ba src/main/java/com/toir/config/DataBootstrap.java | sed -n '1,220p'
nl -ba src/main/java/com/toir/config/SampleDataSeeder.java | sed -n '80,260p'
nl -ba src/main/java/com/toir/config/ExtendedDataSeeder.java | sed -n '45,220p'
nl -ba src/main/resources/application-prod.yml | sed -n '50,75p'
nl -ba src/main/resources/application-dev.yml | sed -n '50,75p'
python3 - <<'PY'
# Extracted JPA entity table names for coverage verification.
PY
```
