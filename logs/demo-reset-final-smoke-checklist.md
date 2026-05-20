# Demo Reset Final Smoke Checklist

Date: 2026-05-20
Branch: `behzod`

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
| Seed draft | PASS | `logs/demo_seed_output.txt` contains successful insert output after the equipment `ON CONFLICT (id)` fix. |
| Validation | PASS | `logs/demo_reset_validation_output.txt` shows admin/role checks OK, demo counts present, and core relation orphan checks at 0. |
| Orphan checks | PASS | 16 checked core relations returned `orphan_count = 0` in `logs/demo_reset_validation_output.txt`. |
| Backend startup | PASS | User-provided context: backend started successfully on restored demo DB. |
| Production DB | PASS | Production DB was not modified; only dump/read access was used for the restore/dry run. |

## 3. Demo Data Counts

Source: `logs/demo_reset_validation_output.txt`.

| Area | Count |
|---|---:|
| Departments | 4 |
| Demo users | 10 |
| Employees | 5 |
| Equipment | 3 |
| Warehouses | 2 |
| Warehouse stock | 3 |
| PPR tasks | 2 |
| Repair requests | 2 |
| Work orders | 2 |
| Procurement requests | 1 |
| Actual costs | 1 |
| Approvals | 1 |
| Inspection results | 2 |
| Knowledge articles | 2 |

Additional validation evidence:

- `SYSTEM_ADMIN` wildcard status: `OK`, matching roles: 1.
- Active admin users: 4.
- Warehouse stock includes low-stock demo seal example.
- Work order smoke data includes `DEMO-WO-2026-0001` in progress and `DEMO-WO-2026-0002` approved.
- PPR smoke data includes `DEMO-PPR-TASK-001` approved and `DEMO-PPR-TASK-002` planned.
- Approval smoke data includes one pending procurement approval with two steps.
- Inspection smoke data includes completed `DEMO-IR-AMM-SHIFT` route round with two results.

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

Status: ready for approval review.

The local restored `toir_demo` dry run is successful, validation passed, and checked core relation orphan counts are zero. Real execution remains blocked until all approval gates in Section 5 are complete and the target database is reconfirmed immediately before execution.

Do not rerun delete/seed scripts against production until fresh backup, restore test, maintenance mode, integration isolation, and signoffs are complete.
