# Phase A0 Live DB Constraint and Orphan Data Audit

Audit date: 2026-05-20

Scope: audit only. No Java source, migrations, applied migrations, DTOs, tests, schema objects, constraints, indexes, or database data were changed.

Generated SQL: `src/main/resources/db/manual/audit_fk_orphans_20260520.sql`

## 1. Executive Summary

DB accessibility: not available from this environment.

- `psql` is installed locally.
- Local configured dev endpoint `localhost:5433/toir` returned no response.
- Configured remote endpoint host `192.168.1.7:5432/toirdb` returned no response.
- Therefore no live/staging catalog queries, orphan checks, or consistency checks were executed against a database in this run.

Output produced:

- A SELECT-only audit script with catalog inventory queries, 91 orphan/semantic relationship checks, and approximately 29 business consistency checks.
- This markdown report with pending live result sections and a safe FK planning matrix.

Top unsafe FK additions until live results are known:

- Any FK from critical workflow tables where orphan count is unknown: `work_orders`, `repair_requests`, `equipment`, `ppr_tasks`, `warehouse_stocks`, `stock_movements`, `repair_material_usages`, `actual_costs`.
- Any actor FK where semantics are unclear: `reporter_id`, `assigned_to_id`, `reviewed_by_id`, `requested_by`, `approved_by`, `author_id`, `responsible_id`.
- Any FK that may conflict with existing soft-deleted parents or cross-department data.

Recommended next step:

1. Run `src/main/resources/db/manual/audit_fk_orphans_20260520.sql` against staging or a production snapshot.
2. Save the output.
3. Populate the orphan and conflict counts in this report before writing any FK or cleanup migration.

## 2. Actual DB Constraint Inventory

Live DB results are pending because no target database was reachable.

The generated SQL script inventories:

- table existence for target tables
- existing foreign keys from `pg_constraint`
- primary, unique, and check constraints from `pg_constraint`
- indexes and partial indexes from `pg_indexes`
- column type/nullability/defaults and `is_deleted` presence from `information_schema.columns`

Expected output matrix after script execution:

| Table | Existing FK | Existing unique/index | Notes |
|---|---|---|---|
| `users` | PENDING | PENDING | Check unique email/username and primary role FK presence |
| `roles` | PENDING | PENDING | Check role code uniqueness and JSONB/check constraints |
| `hr_employees` | PENDING | PENDING | Check user/department/brigade FKs and employee-user uniqueness |
| `hr_timesheet_entries` | PENDING | PENDING | Check employee/work order/cost category indexes/FKs |
| `departments` | PENDING | PENDING | Check code uniqueness and parent hierarchy constraints |
| `brigades` | PENDING | PENDING | Check department/foreman relation constraints |
| `brigade_members` | PENDING | PENDING | Check brigade/user FKs and active uniqueness |
| `locations` | PENDING | PENDING | Check department and parent constraints |
| `equipment` | PENDING | PENDING | Check partial unique code/inventory indexes and type/dept/location/parent FKs |
| `vehicle_details` | PENDING | PENDING | Expected FK/partial unique from `V20260427_1` |
| `warehouses` | PENDING | PENDING | Check department/location/responsible constraints |
| `warehouse_stocks` | PENDING | PENDING | Check warehouse/spare part FKs and active uniqueness |
| `stock_movements` | PENDING | PENDING | Check warehouse/spare/work order constraints |
| `warehouse_equipment_items` | PENDING | PENDING | Expected active unique equipment index; FKs likely absent from migration |
| `repair_material_usages` | PENDING | PENDING | Check work order/warehouse/spare part constraints |
| `ppr_plans` | PENDING | PENDING | Check department FK and period uniqueness |
| `ppr_tasks` | PENDING | PENDING | Check plan/equipment/regulation constraints |
| `repair_requests` | PENDING | PENDING | Check equipment/department/location/actor constraints |
| `work_orders` | PENDING | PENDING | Expected repair request and defect FKs from recent migrations; other FKs pending |
| `defects` | PENDING | PENDING | Expected repair request FK from recent migration; equipment FK pending |
| `defect_lists` | PENDING | PENDING | Check linked origin constraints |
| `defect_list_lines` | PENDING | PENDING | Check parent/defect/spare part constraints |
| `procurement_requests` | PENDING | PENDING | Check department/warehouse/actor constraints |
| `procurement_request_lines` | PENDING | PENDING | Check request/spare part constraints |
| `maintenance_budgets` | PENDING | PENDING | Check department and period constraints |
| `budget_lines` | PENDING | PENDING | Check budget/cost category constraints |
| `actual_costs` | PENDING | PENDING | Check source document and reviewer constraints |
| `actual_cost_review_route_overrides` | PENDING | PENDING | Check actual cost/department constraints |
| `approval_requests` | PENDING | PENDING | Polymorphic document link; index expected, no FK expected |
| `approval_steps` | PENDING | PENDING | Check request FK and request/step uniqueness |
| `inspection_routes` | PENDING | PENDING | Check department constraints |
| `inspection_checkpoints` | PENDING | PENDING | Check route/equipment/location constraints |
| `inspection_rounds` | PENDING | PENDING | Check route constraints |
| `inspection_round_results` | PENDING | PENDING | Check round/checkpoint/defect constraints |
| `knowledge_articles` | PENDING | PENDING | Check linked object constraints and tags JSONB check |

## 3. Orphan Data Results

Live orphan counts are pending.

The script returns each orphan check as:

| Relation | Orphan count | Sample IDs | Risk |
|---|---:|---|---|
| `hr_employees.user_id -> users.id` | PENDING | PENDING | Blocks employee-user FK; semantics must be confirmed |
| `hr_employees.department_id -> departments.id` | PENDING | PENDING | Blocks department FK and PBAC consistency |
| `hr_employees.brigade_id -> brigades.id` | PENDING | PENDING | Blocks brigade FK |
| `hr_timesheet_entries.employee_id -> hr_employees.id` | PENDING | PENDING | Blocks timesheet employee FK |
| `hr_timesheet_entries.work_order_id -> work_orders.id` | PENDING | PENDING | Blocks timesheet work-order FK |
| `equipment.equipment_type_id -> equipment_types.id` | PENDING | PENDING | Blocks equipment type FK |
| `equipment.department_id -> departments.id` | PENDING | PENDING | Blocks operating placement FK |
| `equipment.location_id -> locations.id` | PENDING | PENDING | Blocks location FK |
| `equipment.parent_id -> equipment.id` | PENDING | PENDING | Blocks hierarchy FK |
| `warehouse_stocks.warehouse_id -> warehouses.id` | PENDING | PENDING | Blocks stock warehouse FK |
| `warehouse_stocks.spare_part_id -> spare_parts.id` | PENDING | PENDING | Blocks stock item FK |
| `warehouse_equipment_items.warehouse_id -> warehouses.id` | PENDING | PENDING | Blocks warehouse equipment assignment FK |
| `warehouse_equipment_items.equipment_id -> equipment.id` | PENDING | PENDING | Blocks warehouse equipment assignment FK |
| `stock_movements.warehouse_id/spare_part_id/work_order_id` | PENDING | PENDING | Blocks inventory audit FKs |
| `repair_material_usages.work_order_id/warehouse_id/spare_part_id` | PENDING | PENDING | Blocks material usage FKs |
| `ppr_tasks.plan_id/equipment_id/regulation_id` | PENDING | PENDING | Blocks PPR task FKs |
| `repair_requests.equipment_id/department_id/location_id` | PENDING | PENDING | Blocks repair request FKs |
| `work_orders.equipment_id/department_id/ppr_task_id/warehouse_id/replacement_equipment_id` | PENDING | PENDING | Blocks work order FKs |
| `work_orders.repair_request_id/defect_id` | PENDING | PENDING | Should already be FK-backed if recent migrations applied |
| `defects.equipment_id/repair_request_id` | PENDING | PENDING | Blocks defect FKs |
| `defect_lists.equipment_id/repair_request_id/work_order_id` | PENDING | PENDING | Blocks defect list origin FKs |
| `defect_list_lines.defect_list_id/defect_id/spare_part_id` | PENDING | PENDING | Blocks defect list line FKs |
| `procurement_requests.department_id/warehouse_id` | PENDING | PENDING | Blocks procurement scope FKs |
| `procurement_request_lines.request_id/spare_part_id` | PENDING | PENDING | Blocks procurement line FKs |
| `maintenance_budgets.department_id` | PENDING | PENDING | Blocks budget department FK |
| `budget_lines.budget_id/cost_category_id` | PENDING | PENDING | Blocks budget line FKs |
| `actual_costs.work_order_id/repair_request_id/budget_line_id/cost_category_id` | PENDING | PENDING | Blocks actual cost source FKs |
| `actual_cost_review_route_overrides.actual_cost_id/department_id` | PENDING | PENDING | Blocks route override FKs |
| `approval_steps.request_id -> approval_requests.id` | PENDING | PENDING | Blocks approval step FK |
| `inspection_checkpoints/rounds/results` | PENDING | PENDING | Blocks inspection hierarchy FKs |
| `knowledge_articles.equipment_id/defect_id/work_order_id` | PENDING | PENDING | Blocks knowledge linked-object FKs |

Actor semantic checks are included separately in SQL for both `users.id` and `hr_employees.id`:

- `equipment.responsible_id`
- `repair_requests.reporter_id`
- `repair_requests.assigned_to_id`
- `work_order_tasks.assigned_to_id`
- `procurement_requests.requested_by`
- `procurement_requests.approved_by`
- `actual_costs.reviewed_by_id`
- `knowledge_articles.author_id`

These should not receive FKs until business semantics are confirmed.

## 4. Business Consistency Results

Live conflict counts are pending.

The generated SQL checks:

| Check | Conflict count | Sample IDs | Risk |
|---|---:|---|---|
| Equipment has both `department_id` and active warehouse assignment | PENDING | PENDING | Placement contradiction |
| Equipment has no `department_id` and no active warehouse assignment | PENDING | PENDING | Equipment scope missing |
| Multiple active warehouse assignments for one equipment | PENDING | PENDING | Inventory placement corruption |
| Repair request department differs from equipment department | PENDING | PENDING | Cross-department workflow leak |
| Work order department differs from equipment department | PENDING | PENDING | Work execution scope mismatch |
| Work order department differs from repair request department | PENDING | PENDING | Origin mismatch |
| Work order department differs from PPR plan department through task | PENDING | PENDING | PPR/work-order mismatch |
| Defect equipment department conflicts with repair request department | PENDING | PENDING | Defect scope mismatch |
| Defect list linked equipment/request/work-order departments conflict | PENDING | PENDING | Defect list origin mismatch |
| Procurement request department conflicts with warehouse department | PENDING | PENDING | Warehouse/procurement scope mismatch |
| Actual cost linked source departments conflict | PENDING | PENDING | Finance review scope mismatch |
| Timesheet employee department conflicts with work order department | PENDING | PENDING | Labor accounting mismatch |
| Duplicate active PPR plans by department/year/month | PENDING | PENDING | Planning duplication |
| Duplicate active user email/username | PENDING | PENDING | Login/account ambiguity |
| Duplicate active role code | PENDING | PENDING | RBAC ambiguity |
| Duplicate active equipment code/inventory number | PENDING | PENDING | Registry ambiguity |
| Duplicate active employee.user_id | PENDING | PENDING | User/employee identity ambiguity |
| Duplicate active warehouse stock warehouse+spare part | PENDING | PENDING | Stock double-count risk |
| Duplicate active brigade member brigade+user | PENDING | PENDING | Brigade assignment ambiguity |
| Approved actual costs without review markers | PENDING | PENDING | Financial lifecycle anomaly |
| Completed/closed work orders without start/completion timestamps | PENDING | PENDING | Lifecycle audit gap |
| Completed PPR tasks without actual labor/schedule data | PENDING | PENDING | PPR lifecycle audit gap |
| Received procurement without detectable receipt movement | PENDING | PENDING | Inventory receipt mismatch |

Not detectable from current schema:

- Timesheet approved by owner. The audited entity does not expose `approved_by` or owner approval fields for `hr_timesheet_entries`.

## 5. Safe FK Candidate Matrix

Because live orphan and consistency counts were not available, no FK is safe to add now.

Classification rules for the next run:

- `orphan_count = 0`, no business conflict, clear semantics: `ADD FK ON DELETE RESTRICT`
- optional reference where parent deletion should preserve child: consider `ADD FK ON DELETE SET NULL`
- actor fields with User-vs-Employee ambiguity: `NEEDS BUSINESS CONFIRMATION`
- polymorphic `document_type/document_id`: `NOT ADD YET`, use resolver tests
- orphan count greater than zero: `NOT ADD YET`, cleanup needed first

| Relation | Orphan count | Conflict count | Nullable? | Expected FK action | Safe to add now? | Cleanup needed |
|---|---:|---:|---|---|---|---|
| `work_orders.repair_request_id -> repair_requests.id` | PENDING | PENDING | yes | ADD FK ON DELETE RESTRICT if not already present | No | PENDING |
| `work_orders.defect_id -> defects.id` | PENDING | PENDING | yes | ADD FK ON DELETE RESTRICT if not already present | No | PENDING |
| `defects.repair_request_id -> repair_requests.id` | PENDING | PENDING | yes | ADD FK ON DELETE RESTRICT if not already present | No | PENDING |
| `vehicle_details.equipment_id -> equipment.id` | PENDING | PENDING | no | ADD FK ON DELETE RESTRICT if not already present | No | PENDING |
| `ppr_tasks.plan_id -> ppr_plans.id` | PENDING | PENDING | no | ADD FK ON DELETE RESTRICT | No | PENDING |
| `work_order_tasks.work_order_id -> work_orders.id` | PENDING | PENDING | no | ADD FK ON DELETE RESTRICT | No | PENDING |
| `defect_list_lines.defect_list_id -> defect_lists.id` | PENDING | PENDING | no | ADD FK ON DELETE RESTRICT | No | PENDING |
| `procurement_request_lines.request_id -> procurement_requests.id` | PENDING | PENDING | no | ADD FK ON DELETE RESTRICT | No | PENDING |
| `budget_lines.budget_id -> maintenance_budgets.id` | PENDING | PENDING | no | ADD FK ON DELETE RESTRICT | No | PENDING |
| `approval_steps.request_id -> approval_requests.id` | PENDING | PENDING | no | ADD FK ON DELETE RESTRICT | No | PENDING |
| `inspection_checkpoints.route_id -> inspection_routes.id` | PENDING | PENDING | no | ADD FK ON DELETE RESTRICT | No | PENDING |
| `inspection_rounds.route_id -> inspection_routes.id` | PENDING | PENDING | no | ADD FK ON DELETE RESTRICT | No | PENDING |
| `inspection_round_results.round_id -> inspection_rounds.id` | PENDING | PENDING | no | ADD FK ON DELETE RESTRICT | No | PENDING |
| `equipment.equipment_type_id -> equipment_types.id` | PENDING | PENDING | no | ADD FK ON DELETE RESTRICT | No | PENDING |
| `equipment.department_id -> departments.id` | PENDING | PENDING | yes | ADD FK ON DELETE SET NULL or RESTRICT, needs placement policy | No | PENDING |
| `equipment.location_id -> locations.id` | PENDING | PENDING | yes | ADD FK ON DELETE SET NULL | No | PENDING |
| `equipment.parent_id -> equipment.id` | PENDING | PENDING | yes | ADD FK ON DELETE SET NULL after cycle/orphan audit | No | PENDING |
| `repair_requests.equipment_id -> equipment.id` | PENDING | PENDING | no | ADD FK ON DELETE RESTRICT | No | PENDING |
| `repair_requests.department_id -> departments.id` | PENDING | PENDING | no | ADD FK ON DELETE RESTRICT | No | PENDING |
| `work_orders.equipment_id -> equipment.id` | PENDING | PENDING | no | ADD FK ON DELETE RESTRICT | No | PENDING |
| `work_orders.department_id -> departments.id` | PENDING | PENDING | no | ADD FK ON DELETE RESTRICT | No | PENDING |
| `warehouse_stocks.warehouse_id -> warehouses.id` | PENDING | PENDING | no | ADD FK ON DELETE RESTRICT | No | PENDING |
| `warehouse_stocks.spare_part_id -> spare_parts.id` | PENDING | PENDING | no | ADD FK ON DELETE RESTRICT | No | PENDING |
| `warehouse_equipment_items.warehouse_id -> warehouses.id` | PENDING | PENDING | no | ADD FK ON DELETE RESTRICT | No | PENDING |
| `warehouse_equipment_items.equipment_id -> equipment.id` | PENDING | PENDING | no | ADD FK ON DELETE RESTRICT | No | PENDING |
| actor fields checked against users and employees | PENDING | PENDING | mixed | NEEDS BUSINESS CONFIRMATION | No | PENDING |
| `approval_requests.document_id` polymorphic | n/a | n/a | no | NOT ADD YET; resolver tests only | No | n/a |

## 6. Baseline/Flyway Strategy

Recommended approach: use a production/staging schema snapshot as the authoritative baseline, then add additive safety migrations after the orphan audit is clean.

Rationale:

- Existing production DB already has tables.
- Dev/prod use Hibernate `ddl-auto: validate`, so a clean DB needs schema to exist before app startup.
- Existing Flyway migrations are additive and do not represent full schema creation.
- Applied migrations must not be edited.
- Manual SQL auto-execution is disabled by `spring.sql.init.mode=never`, so manual audit scripts are safe only when run deliberately.

Recommended path:

1. Run Phase A0 SQL on staging and production snapshot.
2. Export schema-only snapshot from the most trusted environment.
3. Create a new baseline strategy document before writing migrations.
4. For existing environments, use Flyway `baseline-on-migrate` carefully and preserve already-applied migration history.
5. Add only additive migrations after counts are known: orphan cleanup first, then FKs/indexes/checks.
6. Keep Hibernate `ddl-auto=validate`; do not return to schema mutation through Hibernate.

Strategy options:

| Option | Assessment |
|---|---|
| Full baseline migration for clean DB creation | Best long-term; requires careful snapshot review to avoid baking drift into source |
| Flyway baseline-on-migrate for existing DB | Already configured; useful but not enough without a canonical baseline script |
| Schema snapshot from production/staging | Recommended source for baseline, after comparing with entities |
| Gradual additive safety migrations only | Lowest immediate risk but does not solve P0 clean-DB reconstruction |

## 7. P0/P1 Fix Plan

Do not implement until Phase A0 live results are filled in.

Batch 0: Execute audit.

- Run the SELECT-only script on staging or a production snapshot.
- Save output and identify all nonzero orphan/conflict counts.
- Confirm actor semantics before any actor FK.

Batch 1: Clean data, no constraints yet.

- Resolve orphans relation by relation.
- Resolve cross-department mismatches.
- Resolve active duplicate uniqueness conflicts.
- Decide soft-deleted parent policy.

Batch 2: Add low-risk FKs.

- Start with child aggregate relations that are semantically clear: approval steps, PPR tasks to plan, work order tasks to work orders, procurement lines to requests, budget lines to budgets, inspection child links.
- Use `ON DELETE RESTRICT` unless the business explicitly wants nulling.

Batch 3: Add scope-critical FKs.

- Equipment/type/department/location.
- Repair request equipment/department/location.
- Work order equipment/department/origin/replacement.
- Warehouse stock/movements/material usage.

Batch 4: Add uniqueness/index hardening.

- Partial unique indexes for active/non-deleted rows.
- Query-aligned indexes for PBAC filters and lifecycle queues.

Batch 5: Baseline migration.

- Produce a canonical schema baseline for clean DB creation.
- Validate clean DB startup with `ddl-auto=validate`.

## 8. SQL Commands Generated/Run

Generated:

- `src/main/resources/db/manual/audit_fk_orphans_20260520.sql`

Validation performed:

- Confirmed script has no top-level write statements: no `INSERT`, `UPDATE`, `DELETE`, `ALTER`, `CREATE`, `DROP`, `TRUNCATE`, `GRANT`, `REVOKE`, or `DO`.
- Confirmed script has 91 orphan/semantic relationship checks.

Commands run:

```bash
command -v psql
pg_isready -h localhost -p 5433 -d toir -U postgres
pg_isready -h 192.168.1.7 -p 5432 -d toirdb -U toir-user
wc -l src/main/resources/db/manual/audit_fk_orphans_20260520.sql
rg -n "^[[:space:]]*(INSERT|UPDATE|DELETE|ALTER|CREATE|DROP|TRUNCATE|GRANT|REVOKE|DO)\\b" src/main/resources/db/manual/audit_fk_orphans_20260520.sql
```

Suggested command for user-run audit:

```bash
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f src/main/resources/db/manual/audit_fk_orphans_20260520.sql
```

## 9. Remaining Questions

- Which database should be treated as the source of truth for baseline: staging, production, or a production snapshot?
- Are actor IDs user IDs, employee IDs, or mixed for reporter/assignee/reviewer/requester/approver/author/responsible fields?
- Should equipment in a warehouse always have `equipment.department_id IS NULL`?
- Should equipment with no department require exactly one active warehouse assignment?
- Should PPR plans be unique by `department_id + year + month`?
- Should optional historical links use `ON DELETE SET NULL` or should all referenced parents be protected by `ON DELETE RESTRICT`?
- Should soft-deleted parent rows remain physically valid FK targets?
- Should procurement receive be traceable to stock movements with a direct procurement reference, or is warehouse/spare/type matching sufficient?
