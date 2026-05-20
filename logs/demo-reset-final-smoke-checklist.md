# Demo Reset Final Smoke Checklist

Date: 2026-05-20
Branch: `behzod`

Status note: this checklist now reflects the successful B3.1 expanded seed dry-run on restored local `toir_demo`. Real execution remains blocked until approvals, fresh backup, restore test, maintenance window, admin confirmation, and integration isolation are complete.

## 1. Local Restored DB Identity

Source: `logs/demo_reset_preflight_output.txt` and `logs/demo_reset_validation_output.txt`.

| Field | Value |
|---|---|
| Database | `toir_demo` |
| User | `postgres` |
| Reported server address | `172.17.0.2` |
| Reported server port | `5432` |
| Documented local URL | `postgresql://postgres:root123@host.docker.internal:5433/toir_demo` |
| Classification | Local restored production snapshot/demo DB, not live production |

## 2. Dry-Run Result

| Check | Status | Evidence |
|---|---|---|
| Restore from snapshot | PASS | User-provided context for restored local `toir_demo`. |
| Preflight | PASS | `logs/demo_reset_preflight_output.txt` shows `toir_demo`, `postgres`, Flyway history, `SYSTEM_ADMIN`, admin users, and row counts. |
| Delete draft | PASS | `logs/demo_reset_delete_output.txt` contains successful command output from the latest rerun. |
| Expanded seed draft | PASS | Expanded deterministic seed completed on restored local `toir_demo`. |
| Validation | PASS | Expanded row-count checks and workflow smoke data passed; no validation blocker reported. |
| Orphan checks | PASS | Core relation orphan checks passed. |
| Backend startup/project run | PASS | Backend/project run worked after the expanded seed. |
| Production DB | PASS | Production DB was not modified; only dump/read access was used for the restore/dry run. |

## 3. Demo Data Counts

Source: B3.1 restored `toir_demo` validation result.

| Area | Count |
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

Additional validation evidence:

- `SYSTEM_ADMIN` wildcard status: `OK`, matching roles: 1.
- Active admin users: 4.
- Warehouse stock includes low-stock and normal-stock examples across 3 warehouses.
- PPR smoke data includes mixed task lifecycle statuses across 75 demo tasks.
- Work order smoke data includes repair, PPR, defect-origin, and replacement-flow examples.
- Procurement, finance, approval, inspection, and knowledge smoke data are present at expanded demo volume.
- Inspection smoke data includes 6 routes, 24 rounds, and 120 results.

## 4. Production DB Status

- Production DB was not modified.
- No destructive SQL was run against production.
- `flyway_schema_history`, `roles`, `SYSTEM_ADMIN`, wildcard permission `*`, admin users, and admin role mappings remain protected by the runbook rules.

## 5. Remaining Approval Gates

| Gate | Status |
|---|---|
| PM approval | REQUIRED before real execution |
| Tech lead approval | REQUIRED before real execution |
| DBA/Ops approval | REQUIRED before real execution |
| Security approval | REQUIRED before real execution |
| Integration owner approval | REQUIRED before real execution |
| Maintenance window | REQUIRED before real execution |
| Fresh backup | REQUIRED immediately before real execution |
| Restore test | REQUIRED on the fresh backup before real execution |
| Admin credential confirmation | REQUIRED before real execution |
| Integrations/webhooks disabled or isolated | REQUIRED before real execution |

## 6. Final Recommendation

Status: ready for approval review with expanded production-like dataset.

The local restored `toir_demo` B3.1 dry run is successful, validation passed, and core relation orphan checks passed. Real execution remains blocked until all approval gates in Section 5 are complete and the target database is reconfirmed immediately before execution.

Do not rerun delete/seed scripts against production until fresh backup, restore test, maintenance mode, integration isolation, and signoffs are complete.
