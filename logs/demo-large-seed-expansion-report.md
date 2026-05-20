# Demo Large Seed Expansion Report

Date: 2026-05-20
Branch: `behzod`

## 0. Follow-Up Fix Note (2026-05-20)

Root cause:

- In expanded `procurement_request_lines` seed SQL, the target column list included `quantity`, but the `SELECT` list skipped it and jumped from `spare_part_id` to `unit`.
- This created a target/SELECT misalignment and broke the intended deterministic insert mapping.

Fix applied:

- File updated: `src/main/resources/db/manual/2026-05-20_demo_seed_draft.sql`
- In the `procurement_request_lines` expanded insert block, added deterministic quantity expression immediately after `spare_part_id`:
  - `(2 + (gs % 10)),`
- This restores column-expression alignment for:
  - `request_id, spare_part_id, quantity, unit, unit_price, estimated_cost, notes`
- Dataset size was not reduced.

## 1. Files Changed

| File | Change |
|---|---|
| `src/main/resources/db/manual/2026-05-20_demo_seed_draft.sql` | Expanded deterministic Navoiyazot-style demo seed with production-like volume across org, HR, equipment, warehouse, PPR, repair, work orders, defects, procurement, finance, approvals, inspections, and knowledge. |
| `src/main/resources/db/manual/2026-05-20_demo_reset_validation.sql` | Added minimum row-count checks and broader demo orphan checks for expanded data. |
| `logs/demo-large-seed-expansion-report.md` | This report. |

No Java code, Flyway migrations, old migrations, preflight SQL, or delete SQL were changed.

## 2. Dataset Counts Before/After

| Area | Previous compact count | Expanded target count |
|---|---:|---:|
| Departments | 4 | 5 |
| Demo users | 10 | 12 |
| Employees | 5 | 25 |
| Locations | 4 | 12 |
| Equipment types | 4 | 8 |
| Equipment | 3 | 60 |
| Warehouses | 2 | 3 |
| Spare parts | 3 | 60 |
| Warehouse stock | 3 | 150 |
| Stock movements | 1 | 67 |
| PPR tasks | 2 | 75 |
| Repair requests | 2 | 40 |
| Work orders | 2 | 50 |
| Defects | 1 | 30 |
| Defect lists | 1 | 10 |
| Defect list lines | 1 | 40 |
| Procurement requests | 1 | 12 |
| Procurement lines | 1 | 36 |
| Maintenance budgets | 1 | 3 |
| Budget lines | 1 | 18 |
| Actual costs | 1 | 30 |
| Approval requests | 1 | 12 |
| Approval steps | 2 | 24 |
| Inspection routes | 1 | 6 |
| Inspection checkpoints | 2 | 36 |
| Inspection rounds | 1 | 24 |
| Inspection results | 2 | 120 |
| Knowledge articles | 2 | 18 |

## 3. Dry-Run Target

Expanded seed dry-run target:

| Field | Value |
|---|---|
| Database | `toir_demo` |
| User | `postgres` |
| Classification | Restored local production snapshot/demo DB, not live production |
| Production DB | Not modified |

The restored local `toir_demo` dry-run was rerun successfully after the earlier local connectivity issue was resolved.

## 4. Preflight/Delete/Seed/Validation Result

| Step | Result | Notes |
|---|---|---|
| Restore fresh `toir_demo` snapshot | PASS | Restored local snapshot/demo DB used for dry-run only. |
| Preflight | PASS | Target/admin/Flyway/role checks passed before reset. |
| Delete draft | PASS | Ordered delete draft completed on restored local `toir_demo`. |
| Expanded seed draft | PASS | Expanded deterministic seed completed on restored local `toir_demo`. |
| Validation | PASS | Expanded row-count checks and core relation checks passed; no validation blocker reported. |
| Backend startup/project run | PASS | Backend/project run worked after the expanded seed. |

## 5. Final Expanded Dataset Counts

| Area | Final validation count |
|---|---:|
| Departments | 5 |
| Demo users | 12 |
| Employees | 25 |
| Locations | 12 |
| Equipment types | 8 |
| Equipment | 60 |
| Warehouses | 3 |
| Spare parts | 60 |
| Warehouse stock | 150 |
| Stock movements | 67 |
| PPR tasks | 75 |
| Repair requests | 40 |
| Work orders | 50 |
| Defects | 30 |
| Defect lists | 10 |
| Defect list lines | 40 |
| Procurement requests | 12 |
| Procurement lines | 36 |
| Maintenance budgets | 3 |
| Budget lines | 18 |
| Actual costs | 30 |
| Approval requests | 12 |
| Approval steps | 24 |
| Inspection routes | 6 |
| Inspection checkpoints | 36 |
| Inspection rounds | 24 |
| Inspection results | 120 |
| Knowledge articles | 18 |

Inspection summary from validation: 6 demo routes, 24 rounds, and 120 results were present after seed. Failed/warn checkpoint examples are included through inspection round results linked to demo defects.

## 6. Orphan Check Result

Expanded orphan checks were added to validation SQL for core demo relationships, including warehouse stock/movements, PPR, repair requests, work orders, material usage, labor/execution, defects, defect lists/lines, procurement, budgets, actual costs, approvals, inspections, and knowledge links.

Result: PASS. Core orphan checks passed with no validation blocker reported after the restored `toir_demo` expanded dry-run.

## 7. Static Safety Review

| Check | Result |
|---|---|
| `git diff --check` | PASS |
| Conflict markers in manual SQL/logs | PASS |
| `DROP` / `TRUNCATE` / `ALTER` in manual reset SQL | PASS, none found |
| `DELETE FROM flyway_schema_history` / `DELETE FROM roles` | PASS, none found |
| Production DB access | PASS, none performed |

ON CONFLICT review:

- Equipment seed remains `ON CONFLICT (id) DO NOTHING`, preserving the prior fix for partial `equipment.code` uniqueness.
- New generated rows use primary-key `ON CONFLICT (id)` where possible.
- Existing validated unique targets are reused for `code`, `username`, `personnel_number`, `number`, `warehouse_id, spare_part_id`, `brigade_id, user_id`, and `request_id, step_number`.
- Expanded seed completed on restored `toir_demo`, so the ON CONFLICT targets used by the draft were accepted by the actual database schema.

## 8. Production Safety Statement

- Production DB was not modified.
- No SQL was executed against production.
- Destructive SQL was run only against restored local `toir_demo`.
- `flyway_schema_history`, `roles`, `SYSTEM_ADMIN`, wildcard permission `*`, admin users, and admin role mappings remain protected by the unchanged delete/preflight rules.
- This expansion remains a manual SQL draft for approval review; it is not authorized for real execution.

## 9. Remaining Approval Gates

- PM approval
- Tech lead approval
- DBA/Ops approval
- Security approval
- Integration owner approval
- Maintenance window
- Fresh backup
- Restore test on the fresh backup
- Admin credential confirmation
- Integrations/webhooks disabled or isolated

## 10. Final Recommendation

Status: ready for approval review with expanded production-like dataset.

The expanded production-like seed passed restored local `toir_demo` dry-run: restore, preflight, delete, seed, validation, core orphan checks, and backend/project run. It is not ready for real execution until all approval gates, a fresh backup, and a restore test on that fresh backup are complete.
