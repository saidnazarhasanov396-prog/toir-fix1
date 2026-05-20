# Demo Large Seed Expansion Report

Date: 2026-05-20
Branch: `behzod`

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

Dry-run target attempted for availability only:

| Target | Result |
|---|---|
| `localhost:5433/toir_demo` user `postgres` | No response |
| `127.0.0.1:5433/toir_demo` user `postgres` | No response |
| `host.docker.internal:5433/toir_demo` user `postgres` | No response |

`DATABASE_URL` / `LOCAL_DEMO_URL` were not set in the shell, and Docker CLI is not available in this environment. Because the restored local `toir_demo` database was not reachable, the expanded seed dry-run was not executed.

## 4. Preflight/Delete/Seed/Validation Result

| Step | Result | Notes |
|---|---|---|
| Restore fresh `toir_demo` snapshot | NOT RUN | No reachable local/demo DB target in current shell. |
| Preflight | NOT RUN for expanded seed | Existing compact dry-run preflight remains PASS in prior logs. |
| Delete draft | NOT RUN for expanded seed | Delete SQL was not changed. |
| Expanded seed draft | NOT RUN | SQL draft updated only; no database writes executed. |
| Validation | NOT RUN for expanded seed | Validation SQL updated only; no database reads against demo DB executed beyond `pg_isready`. |
| Backend startup | NOT RUN for expanded seed | Requires successful restored-demo seed first. |

## 5. Orphan Check Result

Expanded orphan checks were added to validation SQL for core demo relationships, including warehouse stock/movements, PPR, repair requests, work orders, material usage, labor/execution, defects, defect lists/lines, procurement, budgets, actual costs, approvals, inspections, and knowledge links.

Result: not executed for expanded seed because no local restored `toir_demo` target was reachable.

## 6. Static Safety Review

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
- Actual DB catalog verification must be rerun on restored `toir_demo` before approval because the expanded seed introduced additional `ON CONFLICT` clauses for new sections.

## 7. Production Safety Statement

- Production DB was not modified.
- No SQL was executed against production.
- No destructive SQL was run in this phase.
- `flyway_schema_history`, `roles`, `SYSTEM_ADMIN`, wildcard permission `*`, admin users, and admin role mappings remain protected by the unchanged delete/preflight rules.
- This expansion is a manual SQL draft only until a restored local/demo dry-run passes.

## 8. Remaining Approval Gates

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
- Expanded seed dry-run on restored local/demo DB

## 9. Final Recommendation

Status: not ready for real execution.

The expanded production-like seed draft is prepared, but it is not ready for approval review as the execution dataset until it is rerun on a restored local/demo `toir_demo` snapshot with preflight, delete, seed, validation, orphan checks, and backend startup all passing.
