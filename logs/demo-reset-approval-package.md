# Demo Reset Approval Package

Date: 2026-05-20
Branch: `behzod`
Status: Ready for approval review with the expanded production-like seed; not approved for real execution yet.

## 1. Executive Summary

The demo reset package replaces garbage/demo-inconsistent business data with a deterministic Navoiyazot-style demo dataset while preserving schema, migration history, roles, admin access, and security seed data.

The reset scripts were dry-run successfully on a restored local production snapshot/demo database named `toir_demo`. The full sequence passed: restore, preflight, ordered delete, seed, validation, orphan checks, and backend startup.

Production was not modified. Real execution remains blocked until all required signoffs, a fresh backup, restore test, maintenance window, and integration isolation are complete.

Note: B3.1 expanded the manual seed draft after this approval package was first prepared. The expanded dataset has now passed restored local `toir_demo` dry-run. See `logs/demo-large-seed-expansion-report.md`.

## 2. What Was Tested

| Area | Tested |
|---|---|
| Snapshot restore | Restored local `toir_demo` from production snapshot. |
| Preflight | Confirmed target DB identity, Flyway history, `SYSTEM_ADMIN`, wildcard permission, admin users, row counts, and integration/webhook visibility. |
| Delete script | Ordered `DELETE` draft ran on restored local DB only. |
| Seed script | Deterministic demo seed ran on restored local DB after fixing equipment seed conflict target to `ON CONFLICT (id) DO NOTHING`. |
| Validation | Admin/role checks, demo counts, workflow smoke data, and core relation orphan checks passed. |
| Backend startup | Backend started successfully against restored demo DB. |
| B3.1 expanded seed | Expanded production-like dataset passed restored local `toir_demo` dry-run. |

## 3. Dry-Run Evidence

Evidence files:

- `logs/demo_reset_preflight_output.txt`
- `logs/demo_reset_delete_output.txt`
- `logs/demo_seed_output.txt`
- `logs/demo_reset_validation_output.txt`
- `logs/demo_seed_on_conflict_target_check.txt`
- `logs/demo-reset-final-smoke-checklist.md`

| Check | Result | Evidence |
|---|---|---|
| Local restored DB identity | PASS | `toir_demo`, user `postgres`, server address `172.17.0.2`, server port `5432`. |
| Restore from production snapshot | PASS | Restored local `toir_demo` dry-run context. |
| Preflight | PASS | `logs/demo_reset_preflight_output.txt`. |
| Delete draft | PASS | `logs/demo_reset_delete_output.txt`. |
| Seed draft | PASS | `logs/demo_seed_output.txt`. |
| Validation | PASS | `logs/demo_reset_validation_output.txt`. |
| Core orphan checks | PASS | Core relation orphan checks passed; no validation blocker reported. |
| Backend startup | PASS | Backend started successfully on restored `toir_demo`. |

Validated demo data counts from the final checklist:

| Area | Count |
|---|---:|
| Departments | 5 |
| Demo users | 12 |
| Employees | 25 |
| Equipment | 60 |
| Warehouses | 3 |
| Warehouse stock | 150 |
| PPR tasks | 75 |
| Repair requests | 40 |
| Work orders | 50 |
| Procurement requests | 12 |
| Actual costs | 30 |
| Approvals | 12 |
| Inspection routes | 6 |
| Inspection rounds | 24 |
| Inspection results | 120 |
| Knowledge articles | 18 |

## 4. Production Safety

- Production DB was not modified.
- No destructive SQL was run against production.
- Only dump/read access was used to prepare the restored local dry-run DB.
- Destructive reset SQL was tested only on restored local `toir_demo`.
- No Java code, migrations, or old Flyway migrations were changed for this approval package.
- No merge to `main` was performed as part of this package.

## 5. Scripts Involved

| Script | Purpose |
|---|---|
| `src/main/resources/db/manual/2026-05-20_demo_reset_preflight.sql` | SELECT-only target/admin/role/Flyway/row-count/integration preflight. |
| `src/main/resources/db/manual/2026-05-20_demo_reset_delete_draft.sql` | Ordered manual `DELETE` draft, child tables before parents. |
| `src/main/resources/db/manual/2026-05-20_demo_seed_draft.sql` | Deterministic Navoiyazot-style demo seed. |
| `src/main/resources/db/manual/2026-05-20_demo_reset_validation.sql` | SELECT-only validation, row counts, smoke data, and orphan checks. |

## 6. Preserved Data

The reset package is designed to preserve:

- `flyway_schema_history`
- `roles`
- `SYSTEM_ADMIN`
- wildcard permission `*`
- admin users
- admin role mappings

The manual delete draft does not delete from `flyway_schema_history` or `roles`, and it protects admin users/admin role mappings.

## 7. Risks

| Risk | Impact | Required control |
|---|---|---|
| Wrong target DB | Production or unintended DB could be reset | Mandatory target confirmation before every execution phase. |
| Admin lockout | Loss of operational access | Preflight and validation must confirm `SYSTEM_ADMIN`, `*`, admin users, and admin mappings. |
| Integrations/webhooks firing | Real external systems could receive demo events | Disable or isolate integrations/webhooks before execution. |
| `file_assets`/filesystem mismatch | DB references and stored files could diverge | `file_assets` are not cleared by default; separate approved file cleanup is required. |
| No SQL rollback | Reverse SQL cannot reliably restore prior state | Rollback is backup restore only. |
| Long locks during delete | User-facing outage or lock contention | Execute only during approved maintenance window. |

## 8. Required Signoffs

| Signoff | Required confirmation |
|---|---|
| PM | Approves replacing garbage data with the proposed demo dataset. |
| Tech lead | Approves scripts, sequence, validation, and smoke checklist. |
| DBA/Ops | Approves target DB, backup, restore test, maintenance window, and execution operator. |
| Security | Approves admin preservation, demo credential handling, and access controls. |
| Integration owner | Confirms integrations/webhooks are disabled, isolated, or safe. |

## 9. GO/NO-GO Criteria

GO only if all conditions are true:

- PM, Tech lead, DBA/Ops, Security, and Integration owner signoffs are complete.
- Maintenance window is approved and active.
- Fresh backup is completed immediately before execution.
- Fresh backup restore test passes.
- Target DB identity is confirmed by preflight output and human review.
- `SYSTEM_ADMIN`, wildcard `*`, admin users, and admin mappings are confirmed before execution.
- Integrations/webhooks are disabled or isolated.
- Delete and seed scripts are exactly the reviewed versions.
- Validation passes after execution.
- Backend startup and post-execution smoke tests pass.

NO-GO if any condition is true:

- Target DB identity is unclear or unexpected.
- Fresh backup or restore test is missing.
- Any required approval is missing.
- Preflight does not show expected admin/role safety.
- Active integrations/webhooks cannot be disabled or isolated.
- Validation reports nonzero core orphan counts.
- Backend fails to start after reset.

## 10. Recommendation

Ready for approval review.

Not ready for real execution until all signoffs, maintenance window, fresh backup, restore test, admin credential confirmation, and integration isolation are complete.
